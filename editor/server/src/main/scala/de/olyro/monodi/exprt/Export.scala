package de.olyro.monodi
package exprt

import cats.data.*
import cats.implicits.*
import de.olyro.monodi.Util.http.HttpClient
import de.olyro.monodi.Util.{Fence, ZipFile}
import de.olyro.monodi.exprt.iiif.Iiif
import de.olyro.monodi.exprt.Pdf.PdfDoc
import de.olyro.monodi.data.notes.*
import de.olyro.monodi.data.{Document, Source}
import de.olyro.monodi.db.DBRunner
import de.olyro.monodi.db.DBRunner.DBDoobie
import de.olyro.monodi.exprt.Message.{Source as _, *}
import de.olyro.monodi.exprt.Turtle.*
import io.circe.syntax.*
import io.circe.{Encoder, Json}
import filter.ExportFilter
import filter.DocPublishInfo

import zio.interop.catz.*
import zio.stream.*
import zio.{Ref as _, *}

import java.nio.charset.StandardCharsets
import java.nio.file.*
import zio.ZIOAppDefault
import zio.Console.printLine
import zio.ZIO.attemptBlocking
import de.olyro.monodi.exprt.iiif.Iiif.StoreService
import doobie.util.transactor.Transactor

object Export extends ZIOAppDefault:
  lazy val availableProcessors = java.lang.Runtime.getRuntime.availableProcessors()

  type DocStoreData = (Document, Option[RootContainer], Evolutions.EvolutionResult, Source)

  def run: ZIO[Environment & ZIOAppArgs & Scope, Any, Any] =

    def printCompletionMessage(errorState: Severity, svgRoot: Path, out: List[Path], log: Path): UIO[Int] =
      errorState match
        case Error   =>
          printLine(
            s"Had errors, will not publish. The part that could be produced is still under ${out
                .mkString("[", ", ", "]")} and the log under ${log.toString}"
          ).as(1).orDie
        case Warning =>
          printLine(
            s"Successful exprt. TTLs can be found under ${out.mkString("[", ", ", "]")} and the svgs under ${svgRoot.toString}"
          ).as(0).orDie

    def prepare(config: ExportConfig): Task[Unit] =
      attemptBlocking(Files.deleteIfExists(config.logPath)) *>
        attemptBlocking(Files.createDirectories(config.svgPath)) *>
        attemptBlocking(Files.createDirectories(config.mafftPath)) *>
        ZIO.foreachDiscard(config.exportSets.map(_.ttlPath))(p => attemptBlocking(Files.createDirectories(p))) *>
        ZIO.foreachDiscard(config.exportSets.map(_.ttlPath))(p => attemptBlocking(Files.deleteIfExists(p)))

    def writeStage(
        config: ExportConfig,
        rawDefinitions: String,
        contentRoot: Path,
        docStages: List[DocStage],
        more: Accu[Turtle]
    )(
        stage: ExportConfig.ExportSetConfig,
        stageFilter: ExportFilter
    ) = for
      defs <- definitions(rawDefinitions, stage.turtleDefinitionLineReplacements)
      cats <- categories(config.editorPseudonyms, stage.statusWhiteList, docStages)
      cc   <- customContent(stage.extraCss, contentRoot)
      mkDoc = stageDocs(stageFilter)
      docs  = docStages.map(mkDoc).foldK
      srcs <- sources(stageFilter.sourceShouldBeExported, contentRoot)
      ttl   = defs |+| cats |+| cc |+| srcs |+| docs |+| more
      _    <- writeRdf(stage.ttlPath, ttl.data)
      _    <- exportJson(stage.jsonPath, stageFilter)
    yield ttl.log

    def stripDocs(d: DocStage) =
      DocStage(
        d.doc,
        d.onlyTtl.copy(notes = Vector.empty),
        d.withNotes.copy(notes = Vector.empty),
        d.withNotesAndCriticalApparatus.copy(notes = Vector.empty),
        d.commonWarnings,
        d.derivedCategoryValues
      )

    def stageDocs(filter: ExportFilter)(docStage: DocStage): Accu[Turtle] =
      val warnings = Accu.empty.copy(log = docStage.commonWarnings.toVector)
      docStage.doc match
        case None      => warnings
        case Some(doc) =>
          val pubInfo = DocPublishInfo.fromDoc(doc)
          val data    =
            if !filter.documentShouldBeExported(pubInfo) then Accu.empty
            else if filter.apparatusShouldBeExportedFor(pubInfo) then docStage.withNotesAndCriticalApparatus
            else if filter.notesShouldBeExportedFor(pubInfo) then docStage.withNotes
            else docStage.onlyTtl
          data |+| warnings

    def writeDocs(svgPath: Path, data: Accu[?]) = ZIO.foreachDiscard(data.notes):
      case DrawnSvg(hash, svg, _) => writeSvg(svgPath, hash, svg)
      case DrawnPdf(hash, pdf, _) => writePdf(svgPath, hash, pdf)

    def writeSvg(root: Path, basename: String, content: String): Task[Unit] =
      attemptBlocking(Files.writeString(root `resolve` s"$basename.svg", content)).unit

    def writePdf(root: Path, basename: String, content: PdfDoc): Task[Unit] =
      attemptBlocking(Files.write(root `resolve` s"$basename.pdf", content.data)).unit

    def writeRdf(path: Path, statements: Iterable[Turtle]): Task[Unit] =
      attemptBlocking(Files.writeString(path, statements.map(stringify).mkString("\n"))).unit

    def writeLog(path: Path, log: Iterable[Message]): Task[Unit] =
      attemptBlocking(Files.writeString(path, log.map(_.print).mkString("\n"))).unit

    def exportFonts(path: Path): Task[Unit] = attemptBlocking:
      Util.unsafeCopy(Export.getClass, TextBox.normalFontPath, path)
      Util.unsafeCopy(Export.getClass, TextBox.italicFontPath, path)
      Util.unsafeCopy(Export.getClass, TextBox.smcpFontPath, path)

    def loadDocs(
        filter: ExportFilter,
        par: Int
    ): ZStream[DBDoobie & Fence, Throwable, (Document, Option[Json], Source)] =
      val docList           = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.listDocuments))
      def notes(id: String) = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.getDocumentNotes(id)))
      def src(id: String)   = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.getSource(id)).someOrFailException)

      ZStream
        .fromIterableZIO(docList)
        .filter(publishInfo => filter.documentShouldBeExported(DocPublishInfo.fromDoc(publishInfo)))
        .mapZIOPar(par)(doc => ZIO.succeed(doc) <*> notes(doc.id) <*> src(doc.quelle_id))

    (for
      exportArgs    <- ExportConfig.load.orDie
      _             <-
        printLine(
          s"running exprt...\nargs: ${exportArgs.asJson.spaces2}\navailable processors: $availableProcessors"
        ).orDie
      _             <- Fence.measure("db-evolutions")(DBRunner.evolveAllZ.onError(c => printLine(c.prettyPrint).orDie).orDie)
      _             <- prepare(exportArgs).orDie
      rawDefs       <- staticTTL.orDie
      contentRoot   <-
        getContentRoot.mapError(x => new Exception(x.log.map(_.print).mkString("\n"))).orDie // todo handle!
      sourcePubInfo <- DBRunner.runZ(DBRunner.getSourcePublishInfos).orDie
      globalFilter   = ExportFilter.global(sourcePubInfo, exportArgs)
      _              = println("exporting documents...")
      docCreator     = DocCreator(exportArgs.editorPseudonyms, globalFilter)
      _              = println("loading and processing documents...")
      docs          <- loadDocs(globalFilter, availableProcessors)
                         .mapZIOPar(availableProcessors)(docCreator.createNormalDocument)
                         .mapZIO(doc => writeDocs(exportArgs.svgPath, doc.withNotesAndCriticalApparatus).as(stripDocs(doc)))
                         .runCollect
                         .orDie
                         .map(_.toList)
      _              = println(s"processing ${docs.size} documents done.")
      _             <- printLine("processing spiele")
      spieleDocsAll <- makeSpiele(exportArgs.editorPseudonyms, globalFilter).orDie
      spieleDocs    <- ZIO.foreach(spieleDocsAll): doc =>
                         writeDocs(exportArgs.svgPath, doc.withNotesAndCriticalApparatus).as(stripDocs(doc))
      allDocs        = docs ++ spieleDocs
      _             <- printLine(s"total documents to export: ${allDocs.size}")
      more          <- makeExportVersion.map(Accu.data)
      mkStage        = writeStage(exportArgs, rawDefs, contentRoot, allDocs, more)
      log           <-
        ZIO
          .foreach(exportArgs.exportSets)(sc =>
            val filter = ExportFilter.ofStage(sourcePubInfo, sc)
            mkStage(sc, filter)
          )
          .orDie
      hadError       = log.flatMap(_.map(_.severity)).maxOption.getOrElse(Message.Warning)
      _             <- writeLog(exportArgs.logPath, log.flatten).orDie
      _             <- exportFonts(exportArgs.svgPath).ignore
      ret           <-
        printCompletionMessage(hadError, exportArgs.svgPath, exportArgs.exportSets.map(_.ttlPath), exportArgs.logPath)
      _             <- ZIO.attemptBlocking(Files.createFile(Paths.get("/tmp/done-export"))).ignore
      _             <- Fence.results `flatMap` Fence.print
    yield ret)
      .provide(
        Fence.live,
        Iiif.live,
        Util.http.live.orDie,
        DBRunner.live.orDie,
        ContainerEngine.live.orDie
      )

  private def makeSpiele(
      pseudonums: Map[String, String],
      globalFilter: ExportFilter
  ): ZIO[Transactor[Task] & Fence & StoreService & HttpClient & ContainerEngine, Throwable, List[DocStage]] =
    def loadDocs(docs: List[String]): ZStream[DBDoobie & Fence, Throwable, (Document, Option[Json], Source)] =
      def loadDoc(id: String) = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.getDocument(id)).someOrFailException)
      def notes(id: String)   = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.getDocumentNotes(id)))
      def src(id: String)     = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.getSource(id)).someOrFailException)

      ZStream
        .fromIterable(docs)
        .mapZIOPar(availableProcessors)(loadDoc)
        .filter(publishInfo => globalFilter.documentShouldBeExported(DocPublishInfo.fromDoc(publishInfo)))
        .mapZIOPar(availableProcessors)(doc => ZIO.succeed(doc) <*> notes(doc.id) <*> src(doc.quelle_id))

    for
      spieleInfo <- DBRunner.runZ(DBRunner.getSpieleInfo).orDie
      _          <- printLine(s"found ${spieleInfo.size} spiele to export").orDie
      docStages  <- ZIO.foreach(spieleInfo.toList): (ref, docIds) =>
                      for
                        docs   <- loadDocs(docIds).runCollect
                        stages <- DocCreator(pseudonums, globalFilter).createSpiel(ref, docs.toList)
                      yield stages
    yield docStages

  val staticTTL = Fence.measure("make-static"):
    for
      definitions <- attemptBlocking(Util.unsafeReadResource(Export.getClass, "definitions.ttl"))
      statics     <- attemptBlocking(Util.unsafeReadResource(Export.getClass, "static.ttl"))
    yield definitions + "\n" + statics

  def definitions(
      rawDefs: String,
      replacers: List[ExportConfig.TurtleDefinitionsLineReplacer]
  ): URIO[Fence, Accu[Turtle]] =
    Fence.measurePure("make-definitions")(
      Accu.data(ExportConfig.TurtleDefinitionsLineReplacer.applyAll(replacers)(rawDefs))
    )

  def getSourcesToBeExported(whiteList: List[String]): ZIO[DBDoobie, Throwable, List[String]] = db.DBRunner
    .runZ(db.DBRunner.listSources)
    .map(sources =>
      sources
        .filter(s => whiteList.isEmpty || whiteList.contains(s.publish.toLowerCase.trim))
        .map(_.id),
    )

  def categories(
      pseudonyms: Map[String, String],
      whiteList: List[String],
      docStages: List[DocStage]
  ): URIO[Fence & DBDoobie, Accu[Turtle]] =
    val editionsstatusGuard = Some(CategoryDescription.Guard("editionsstatus", dataUri("documentEditionsstatus")))

    val categories = List(
      // format: off
      CategoryDescription("documentBibliographischerverweis", isNativeColumn = true,  "bibliographischerverweis", DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("documentQuelle_id",                isNativeColumn = true,  "quelle_id",                DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("documentFesttag",                  isNativeColumn = true,  "festtag",                  DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("documentTextinitium",              isNativeColumn = true,  "textinitium",              DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("Melodiennummer_Katalog",           isNativeColumn = false, "Melodiennummer_Katalog",   DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("Melodie_Standard",                 isNativeColumn = false, "Melodie_Standard",         DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("Editor",                           isNativeColumn = false, "Editor",                   DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("SpielName",                        isNativeColumn = false, "SpielName",                DocumentCategoryFilter(whiteList), editionsstatusGuard),

      CategoryDescription("sourceQuellensigle",               isNativeColumn = true,  "quellensigle",             SourceCategoryFilter(whiteList), None),
      CategoryDescription("sourceHerkunftsregion",            isNativeColumn = true,  "herkunftsregion",          SourceCategoryFilter(whiteList), None),
      CategoryDescription("sourceHerkunftsort",               isNativeColumn = true,  "herkunftsort",             SourceCategoryFilter(whiteList), None),
      CategoryDescription("sourceHerkunftsinstitution",       isNativeColumn = true,  "herkunftsinstitution",     SourceCategoryFilter(whiteList), None),
      CategoryDescription("sourceQuellentyp",                 isNativeColumn = true,  "quellentyp",               SourceCategoryFilter(whiteList), None),
      CategoryDescription("sourceBibliotheksort",             isNativeColumn = true,  "bibliotheksort",           SourceCategoryFilter(whiteList), None),
      CategoryDescription("sourceBibliothek",                 isNativeColumn = true,  "bibliothek",               SourceCategoryFilter(whiteList), None),
      CategoryDescription("sourceBibliothessignatur",         isNativeColumn = true,  "bibliothekssignatur",      SourceCategoryFilter(whiteList), None),
      // format: on
    )

    def getValues(c: CategoryDescription): URIO[DBDoobie, List[CategoryValue]] =
      db.DBRunner.runZ(db.DBRunner.listDistinct_UNSAFE_CAN_LEAD_TO_SQL_INJECTION(c)).orDie

    def makeRdf(c: CategoryDescription, values: List[CategoryValue]) = values match
      case Nil    => Option.empty
      case h :: t =>
        Some(
          Statement(
            dataUri(c.propertyName),
            NonEmptyList(h, t).map(v =>
              viewUri("hasPossibleValue") -> (
                v.guard match
                  case None    => Blank(viewUri("value") -> LString(pseudonyms.getOrElse(v.value.trim, v.value)))
                  case Some(g) =>
                    Blank(
                      viewUri("value") -> LString(pseudonyms.getOrElse(v.value.trim, v.value)),
                      viewUri("guard") -> Blank(
                        viewUri("attribute") -> g.attribute,
                        viewUri("value")     -> LString(g.value)
                      )
                    )
              )
            )
          )
        )

    val edierteGattungen = db.DBRunner
      .runZ(db.DBRunner.getDistinctGattung1)
      .orDie
      .map:
        case Nil    => Option.empty
        case h :: t =>
          Some(
            Statement(
              dataUri("sourceEditierteGattungen"),
              NonEmptyList(h, t).map(v =>
                viewUri("hasPossibleValue") ->
                  Blank(viewUri("value") -> LString(v)),
              )
            )
          )

    val derivedCategories: List[Turtle] =
      val aggregated = docStages
        .flatMap(_.derivedCategoryValues.toList)
        .groupMapReduce(_._1)(_._2)(_ ++ _)
        .map((propName, values) => (propName, values.distinct))

      aggregated.flatMap { (propName, values) =>
        NonEmptyList.fromList(values).map { nel =>
          Statement(
            dataUri(propName),
            nel.map(v => viewUri("hasPossibleValue") -> Blank(viewUri("value") -> LString(v)))
          )
        }
      }.toList

    Fence.measure("make-categories")(for
      cats <- ZIO.foreach(categories)(c => getValues(c).map(makeRdf(c, _)))
      egs  <- edierteGattungen
    yield Accu.allData(cats.flatten ++ egs ++ derivedCategories))

  def customContent(extraCss: String, contentRoot: Path): URIO[Fence, Accu[Turtle]] =

    final case class CustomContent(ref: Ref, name: String, additionalData: String, language: Option[String])

    def files(extraCSS: String) =
      CustomContent(viewUri("customCss"), "custom.css", extraCSS, None) ::
        List("de", "en", "fr").flatMap(lang =>
          List(
            CustomContent(dataUri("about"), "about.html", "", Some(lang)),
            CustomContent(dataUri("foerdervermerk"), "foerdervermerk.html", "", Some(lang)),
            CustomContent(dataUri("datenschutz"), "datenschutz.html", "", Some(lang)),
            CustomContent(dataUri("impressum"), "impressum.html", "", Some(lang)),
            CustomContent(dataUri("feedback"), "feedback.html", "", Some(lang))
          ),
        )

    def makeOne(file: CustomContent): UIO[Accu[Turtle]] =
      val fileName = file.language.fold(file.name)(lang => lang + "_" + file.name)
      ZIO.succeed(println(s"Reading ${contentRoot `resolve` fileName}")) *>
        ZIO
          .attempt({ new String(Files.readAllBytes(contentRoot `resolve` fileName), StandardCharsets.UTF_8) })
          .fold(
            _ =>
              Accu.logError(FromCustomFile(contentRoot `resolve` fileName), s"Could not read for ${file.ref.toString}"),
            content =>
              Accu.data(
                Statement(file.ref, viewUri("hasContent") -> LString(content + file.additionalData, file.language))
              ),
          )

    Fence.measure("make-custom-content")(ZIO.foreach(files(extraCss))(makeOne).map(_.foldK))

  def sources(
      sourceFilter: String => Boolean,
      contentRoot: Path
  ): URIO[Fence & DBDoobie, Accu[Turtle]] =
    def getGattung1String(id: String) =
      db.DBRunner.runZ(db.DBRunner.getDistinctGattung1ForQuelle(id)).map(_.sorted.mkString(", ")).orDie

    def getDescriptionString(id: String): UIO[String] =
      val path = contentRoot `resolve` "quellenbeschreibungen" `resolve` s"${id.replaceAll("(?U)\\P{Alnum}", "_")}.html"
      attemptBlocking(Files.readString(path, StandardCharsets.UTF_8))
        .orElseSucceed("Zu dieser Quelle existiert noch keine Beschreibung.")

    def mkRdf(s: Source) = for
      editierteGattung <- getGattung1String(s.id)
      description      <- getDescriptionString(s.id)
    yield Statement(
      dataUri(s.id),
      a                                     -> dataUri("source"),
      dataUri("sourceQuellensigle")         -> LString(s.quellensigle),
      dataUri("sourceHerkunftsregion")      -> LString(s.herkunftsregion),
      dataUri("sourceHerkunftsort")         -> LString(s.herkunftsort),
      dataUri("sourceHerkunftsinstitution") -> LString(s.herkunftsinstitution),
      dataUri("sourceOrdenstradition")      -> LString(s.ordenstradition),
      dataUri("sourceQuellentyp")           -> LString(s.quellentyp),
      dataUri("sourceBibliotheksort")       -> LString(s.bibliotheksort),
      dataUri("sourceBibliothek")           -> LString(s.bibliothek),
      dataUri("sourceBibliothessignatur")   -> LString(s.bibliothekssignatur),
      dataUri("sourceDatierung")            -> LString(s.datierung),
      dataUri("sourceKommentar")            -> LString(s.kommentar),
      dataUri("sourceEditierteGattungen")   -> LString(editierteGattung),
      dataUri("sourceBeschreibung")         -> LString(description),
      dataUri("sourceJahrhundert")          -> LString(s.jahrhundert)
    )

    Fence.measure("make-sources")(for
      src <- db.DBRunner.runZ(db.DBRunner.listSources).orDie
      // todo: make a source stage data structure and filter later so we can reuse rdfs for multiple stages
      res <- ZIO.foreach(src)(s => if sourceFilter(s.id) then mkRdf(s).map(Accu.data) else ZIO.succeed(Accu.empty))
    yield res.foldK)

  case class DocStage(
      doc: Option[Document],
      onlyTtl: Accu[Turtle],
      withNotes: Accu[Turtle],
      withNotesAndCriticalApparatus: Accu[Turtle],
      commonWarnings: List[Message],
      derivedCategoryValues: Map[String, List[String]]
  )

  def makeExportVersion: URIO[Fence, Turtle] = for now <- zio.Clock.currentDateTime.map(_.toInstant)
  yield Statement(dataUri("version"), label -> LString(now.toString))

  type UStream[+A]      = ZStream[Any, Nothing, A]
  type URStream[-R, +A] = ZStream[R, Nothing, A]

  def dataUri(s: String): Ref =
    Ref(java.net.URI.create("http://monodicum/" + java.net.URLEncoder.encode(s, "UTF-8")))
  def viewUri(s: String): Ref =
    Ref(java.net.URI.create("http://olyro.de/mondiview/" + java.net.URLEncoder.encode(s, "UTF-8")))
  def a: Ref                  =
    Ref(java.net.URI.create("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"))
  def label: Ref              =
    Ref(java.net.URI.create("http://www.w3.org/2000/01/rdf-schema#label"))

  def getContentRoot: IO[Accu[Turtle], Path] =
    val possibleRoots = List(
      Paths.get("./content/"),
      Paths.get("./data/content/"),
      Paths.get("../monodi-data/content/"),
      Paths.get("../../monodi-data/content/"),
      Paths.get("../../../data/content")
    )

    possibleRoots
      .findM(root => ZIO.succeed { Files.exists(root) })
      .someOrFail(
        Accu.logError(NoCustomDir, s"Could not find custom data folder. Looked in ${possibleRoots.toString}")
      )

  def exportJson(
      root: Option[Path],
      filter: ExportFilter
  ): ZIO[Fence & DBDoobie, Nothing, Unit] =
    def toBytes[A: Encoder](a: A): Array[Byte] =
      a.asJson.spaces2.getBytes(StandardCharsets.UTF_8)

    def exportSourceAsJson(zip: ZipFile)(sourceId: String) =
      for
        source <- db.DBRunner.runZ(db.DBRunner.getSource(sourceId)).someOrFailException.orDie
        _      <- zip =<< toBytes(source) -> List(source.id, "meta.json")
      yield ()

    def maybeExportDocument(zip: ZipFile)(docId: String) =
      for
        doc   <- db.DBRunner.runZ(db.DBRunner.getDocument(docId)).someOrFailException.orDie
        _     <-
          (zip =<< toBytes(doc) -> List(doc.quelle_id, docId, "meta.json"))
            .when(filter.documentShouldBeExported(DocPublishInfo.fromDoc(doc)))
        notes <- db.DBRunner.runZ(db.DBRunner.getDocumentNotes(docId)).orDie
        _     <- ZIO
                   .foreachDiscard(notes)(n => zip =<< toBytes(n) -> List(doc.quelle_id, docId, "data.json"))
                   .when(filter.notesShouldBeExportedFor(DocPublishInfo.fromDoc(doc)))
      yield ()

    ZIO
      .fromOption(root)
      .flatMap(path =>
        ZIO.scoped {
          ZipFile
            .make(path)
            .flatMap(zip =>
              Fence.measure("exprt-json")(
                for
                  sourcesToBeExported <-
                    db.DBRunner.runZ(db.DBRunner.listSources).map(_.map(_.id).filter(filter.sourceShouldBeExported))
                  _                   <- ZIO.foreachDiscard(sourcesToBeExported)(exportSourceAsJson(zip))
                  documentIds         <- db.DBRunner.runZ(db.DBRunner.listDocumentIds).orDie
                  _                   <- ZIO.foreachDiscard(documentIds)(maybeExportDocument(zip))
                yield ()
              ),
            )
            .orDie
        },
      )
      .ignore
