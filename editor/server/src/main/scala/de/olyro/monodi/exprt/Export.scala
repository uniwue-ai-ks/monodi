package de.olyro.monodi
package exprt

import cats.data.*
import cats.implicits.*
import de.olyro.monodi.Util.http.HttpClient
import de.olyro.monodi.Util.{Fence, ZipFile}
import de.olyro.monodi.exprt.iiif.Iiif
import Iiif.ManifestStore
import de.olyro.monodi.exprt.Pdf.PdfDoc
import de.olyro.monodi.data.notes.*
import de.olyro.monodi.data.{Document, Source}
import de.olyro.monodi.db.DBRunner
import de.olyro.monodi.db.DBRunner.DBDoobie
import de.olyro.monodi.exprt.Message.{Source as _, *}
import de.olyro.monodi.exprt.Turtle.*
import io.circe.syntax.*
import io.circe.{Encoder, Json}
import scalatags.text.Frag

import zio.interop.catz.*
import zio.stream.*
import zio.{Ref as _, *}

import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.text.Normalizer
import scala.util.chaining.*
import de.olyro.monodi.exprt.print.Print
import zio.ZIOAppDefault
import zio.Console.printLine
import zio.ZIO.attemptBlocking

object Export extends ZIOAppDefault:
  lazy val availableProcessors = java.lang.Runtime.getRuntime.availableProcessors()

  sealed trait ExportFilter:
    def sourceShouldBeExported(src: String): Boolean
    def documentShouldBeExported(doc: Document): Boolean
    def notesShouldBeExportedFor(doc: Document): Boolean
    def apparatusShouldBeExportedFor(doc: Document): Boolean

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
      cats <- categories(config.editorPseudonyms, stage.statusWhiteList)
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
        d.commonWarnings
      )

    def stageDocs(filter: ExportFilter)(docStage: DocStage): Accu[Turtle] =
      val warnings = Accu.empty.copy(log = docStage.commonWarnings.toVector)
      docStage.doc match
        case None      => warnings
        case Some(doc) =>
          val data =
            if !filter.documentShouldBeExported(doc) then Accu.empty
            else if filter.apparatusShouldBeExportedFor(doc) then docStage.withNotesAndCriticalApparatus
            else if filter.notesShouldBeExportedFor(doc) then docStage.withNotes
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

    val loadDocs: ZStream[DBDoobie & Fence, Throwable, (Document, Option[Json], Source)] =
      val docList           = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.listDocuments))
      def notes(id: String) = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.getDocumentNotes(id)))
      def src(id: String)   = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.getSource(id)).someOrFailException)

      ZStream.fromIterableZIO(docList).mapZIO(doc => ZIO.succeed(doc) <*> notes(doc.id) <*> src(doc.quelle_id))

    (for
      exportArgs   <- ExportConfig.load.orDie
      _            <-
        printLine(
          s"running exprt...\nargs: ${exportArgs.asJson.spaces2}\navailable processors: $availableProcessors"
        ).orDie
      _            <- Fence.measure("db-evolutions")(DBRunner.evolveAllZ.onError(c => printLine(c.prettyPrint).orDie).orDie)
      _            <- prepare(exportArgs).orDie
      rawDefs      <- staticTTL.orDie
      contentRoot  <-
        getContentRoot.mapError(x => new Exception(x.log.map(_.print).mkString("\n"))).orDie // todo handle!
      globalFilter <- mkGlobalFilter(exportArgs).orDie
      mkDocStage    = documentStage(exportArgs.editorPseudonyms, globalFilter, exportArgs.mafftPath)
      docs         <- loadDocs
                        .mapZIOPar(availableProcessors)(mkDocStage)
                        .mapZIO(doc => writeDocs(exportArgs.svgPath, doc.withNotesAndCriticalApparatus).as(stripDocs(doc)))
                        .runCollect
                        .orDie
                        .map(_.toList)
      more         <- alternativeSortingAttributes.zipWith(makeExportVersion)(_ `addData` _)
      mkStage       = writeStage(exportArgs, rawDefs, contentRoot, docs, more)
      log          <- ZIO.foreach(exportArgs.exportSets)(sc => mkStageFilter(sc).flatMap(sf => mkStage(sc, sf)).orDie)
      hadError      = log.flatMap(_.map(_.severity)).maxOption.getOrElse(Message.Warning)
      _            <- writeLog(exportArgs.logPath, log.flatten).orDie
      _            <- exportFonts(exportArgs.svgPath).ignore
      ret          <-
        printCompletionMessage(hadError, exportArgs.svgPath, exportArgs.exportSets.map(_.ttlPath), exportArgs.logPath)
      _            <- ZIO.attemptBlocking(Files.createFile(Paths.get("/tmp/done-export"))).ignore
      _            <- Fence.results `flatMap` Fence.print
    yield ret)
      .provide(
        Fence.live,
        Iiif.live,
        Util.http.live.orDie,
        DBRunner.live.orDie,
        ContainerEngine.live.orDie
      )

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

  def mkGlobalFilter(config: ExportConfig): ZIO[DBDoobie, Throwable, ExportFilter] =
    val makeAllDocs              = config.exportSets.exists(_.statusWhiteList.isEmpty)
    val makeAllNotes             = config.exportSets.exists(_.documentNoteStatusWhiteList.isEmpty)
    val makeAllApparati          = config.exportSets.exists(_.documentApparatusStatusWhiteList.isEmpty)
    val globalWhiteList          = config.exportSets.flatMap(_.statusWhiteList).distinct
    val globalNotesWhiteList     = config.exportSets.flatMap(_.documentNoteStatusWhiteList).distinct
    val globalApparatusWhiteList = config.exportSets.flatMap(_.documentApparatusStatusWhiteList).distinct

    for swl <- getSourcesToBeExported(if makeAllDocs then Nil else globalWhiteList)
    yield new ExportFilter:
      override def sourceShouldBeExported(src: String): Boolean         = swl.contains(src)
      override def documentShouldBeExported(doc: Document): Boolean     = makeAllDocs ||
        (sourceShouldBeExported(doc.quelle_id) && globalWhiteList.contains(doc.publish.toLowerCase.trim))
      override def notesShouldBeExportedFor(doc: Document): Boolean     = documentShouldBeExported(doc) &&
        (makeAllNotes || globalNotesWhiteList.contains(doc.publish.toLowerCase.trim))
      override def apparatusShouldBeExportedFor(doc: Document): Boolean = notesShouldBeExportedFor(
        doc
      ) && (makeAllApparati || globalApparatusWhiteList.contains(doc.publish.toLowerCase.trim))

  def mkStageFilter(config: ExportConfig.ExportSetConfig) = for swl <- getSourcesToBeExported(config.statusWhiteList)
  yield new ExportFilter:
    override def sourceShouldBeExported(src: String): Boolean         = swl.contains(src)
    override def documentShouldBeExported(doc: Document): Boolean     = swl.contains(doc.quelle_id) &&
      (config.statusWhiteList.isEmpty || config.statusWhiteList.contains(doc.publish.toLowerCase.trim))
    override def notesShouldBeExportedFor(doc: Document): Boolean     =
      documentShouldBeExported(doc) &&
        (config.documentNoteStatusWhiteList.isEmpty || config.documentNoteStatusWhiteList.contains(
          doc.publish.toLowerCase.trim
        ))
    override def apparatusShouldBeExportedFor(doc: Document): Boolean =
      notesShouldBeExportedFor(doc) &&
        (config.documentApparatusStatusWhiteList.isEmpty || config.documentApparatusStatusWhiteList.contains(
          doc.publish.toLowerCase.trim
        ))

  def categories(
      pseudonyms: Map[String, String],
      whiteList: List[String]
  ): URIO[Fence & DBDoobie, Accu[Turtle]] =
    val editionsstatusGuard = Some(CategoryDescription.Guard("editionsstatus", dataUri("documentEditionsstatus")))

    val categories = List(
      // format: off
      CategoryDescription("documentBibliographischerverweis", isNativeColumn = true,  "bibliographischerverweis", DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("documentQuelle_id",                isNativeColumn = true,  "quelle_id",                DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("documentGattung1",                 isNativeColumn = true,  "gattung1",                 DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("documentFesttag",                  isNativeColumn = true,  "festtag",                  DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("documentTextinitium",              isNativeColumn = true,  "textinitium",              DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("Melodiennummer_Katalog",           isNativeColumn = false, "Melodiennummer_Katalog",   DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("Melodie_Standard",                 isNativeColumn = false, "Melodie_Standard",         DocumentCategoryFilter(whiteList), editionsstatusGuard),
      CategoryDescription("Editor",                           isNativeColumn = false, "Editor",                   DocumentCategoryFilter(whiteList), editionsstatusGuard),

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

    Fence.measure("make-categories")(for
      cats <- ZIO.foreach(categories)(c => getValues(c).map(makeRdf(c, _)))
      egs  <- edierteGattungen
    yield Accu.allData(cats.flatten ++ egs))

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
      commonWarnings: List[Message]
  )

  def documentStage(pseudonyms: Map[String, String], filter: ExportFilter, mafftPath: Path): (
      (Document, Option[Json], Source)
  ) => zio.ZIO[Iiif.StoreService & HttpClient & Fence & ContainerEngine, Nothing, DocStage] =
    import Accu.*

    def parseNotes(
        doc: Document,
        rawNotes: Option[Json]
    ): ZIO[Fence, Accu[Nothing], (RootContainer, Evolutions.EvolutionResult)] = Fence.measure("docs-evolve")(
      ZIO
        .fromEither(rawNotes.toRight("could not load notes"))
        .map(j => Container.parse(j.printWith(DBRunner.jsonPrinter)))
        .absolve
        .mapError(Accu.logWarning(Message.FromDoc(doc), _))
    )

    def mainRdf(doc: Document): URIO[Fence, Accu[Turtle]] = Fence.measurePure("docs-main-rdf")(Accu.data({
      val mainArgs = List(
        dataUri("documentQuelle")                   -> dataUri(doc.quelle_id),
        dataUri("documentQuelle_id")                -> LString(doc.quelle_id),
        dataUri("documentDokumenten_id")            -> LString(doc.dokumenten_id),
        dataUri("documentGattung1")                 -> LString(doc.gattung1),
        dataUri("documentGattung2")                 -> LString(doc.gattung2),
        dataUri("documentFesttag")                  -> LString(doc.festtag),
        dataUri("documentFeier")                    -> LString(doc.feier),
        dataUri("documentTextinitium")              -> LString(doc.textinitium),
        dataUri("documentBibliographischerverweis") -> LString(doc.bibliographischerverweis),
        dataUri("documentDruckausgabe")             -> LString(doc.druckausgabe),
        dataUri("documentZeilenstart")              -> LString(doc.zeilenstart),
        dataUri("documentFoliostart")               -> LString(doc.foliostart),
        dataUri("documentKommentar")                -> LString(doc.kommentar),
        dataUri("documentEditionsstatus")           -> LString(doc.editionsstatus)
      )

      val additionalArgs = doc.additionalData.toList.map({ case (key, value) =>
        dataUri(key) -> LString(pseudonyms.getOrElse(value.trim, value))
      })

      Statement(
        dataUri(doc.id),
        a -> dataUri("document"),
        (mainArgs ++ additionalArgs)*
      )
    }))

    def comments(doc: Document, notes: RootContainer): URIO[Fence, Accu[Turtle]] =
      Fence.measurePure("docs-comments")({
        import scalatags.Text.all.{Frag as _, p as _, *}

        val collectionName = "htmlImageCollection_" + doc.id
        val commentData    = ViewModel.getCommentData(notes)

        def makeHtmlRdf(frag: Frag): Accu[Int => Turtle] =
          Accu.data(i =>
            Statement(
              dataUri(collectionName),
              viewUri("hasMember") -> Blank(
                viewUri("orderNumber") -> LInt(i),
                viewUri("html")        -> LString(frag.toString)
              )
            ),
          )

        def makeNoteRdf(dn: DrawnNotes): Int => Turtle =
          i =>
            Statement(
              dataUri(collectionName),
              viewUri("hasMember") -> Blank(
                viewUri("orderNumber") -> LInt(i),
                viewUri("fileName")    -> LString(dn.hash + ".svg")
              )
            )

        def makeParatext(p: ParatextComment): Accu[Int => Turtle] = {
          import scalatags.Text.all.{p as _, *}

          p.tree match {
            case None =>
              makeHtmlRdf(
                table(`class` := "paratext-table")(
                  tr()(
                    td()("Emendation"),
                    td()(if p.emendation then "Ja" else "Nein")
                  ),
                  tr()(
                    td()("Alternativer Text"),
                    td()(p.alternativeText)
                  ),
                  tr()(
                    td()("Kommentar"),
                    td()(p.comment)
                  )
                )
              )

            case Some(tree) =>
              val svg = new comment_tree.CommentTreeSvg(Svg.defaultSvg).draw(tree, None, notes)
              Accu.note(DrawnNotes.make(Svg.defaultSvg)(svg)).dataFromNote(makeNoteRdf)

          }
        }

        val container = Accu.data((i: Int) =>
          Statement(
            dataUri(collectionName),
            viewUri("hasMember") -> Blank(
              viewUri("orderNumber")     -> LInt(i),
              viewUri("blockStartClass") -> LString("monodi-comment-block")
            )
          ): Turtle,
        )

        def makeIntro(cd: CommentData): Accu[Int => Turtle] = {
          val openId = cd.comment match {
            case Left(_)  => cd.container.uuid
            case Right(c) => c.concatenatedIds
          }

          val headline = makeHtmlRdf(
            h2(`class` := "notes-headline", id := s"popup__autoopen__$openId")(cd.signatures.mkString(" "))
          )

          container |+| headline
        }

        def makeComment(c: CommentData): Accu[Int => Turtle] =
          makeIntro(c) |+| (c.comment match {
            case Left(pc) => makeParatext(pc)

            case Right(c) =>
              val metaData = makeHtmlRdf(
                div(`class` := "part")(
                  if c.tree.nonEmpty then
                    div()
                  else
                    table(`class` := "notes-table")(
                      tr()(
                        td()("Emendation:"),
                        td()(c.emendation.fold("Ja")(b => if b then "Ja" else "Nein"))
                      ),
                      tr()(
                        td()("Erklärung:"),
                        td()(c.text)
                      )
                    ),
                )
              )

              val cut = Cutter.cut(notes, c.startUUID, c.endUUID).orElse(Cutter.cut(notes, c.endUUID, c.startUUID))

              val originalPart: Accu[Int => Turtle] = cut match {
                case Left(())             =>
                  Accu.logWarning(FromDoc(doc), s"Chould not cut document at ${c.startUUID} and ${c.endUUID}")
                case Right(None)          => Accu.empty
                case Right(Some(newRoot)) =>
                  val svg = Svg.defaultSvg.drawAll(newRoot)
                  Accu.note(DrawnNotes.make(Svg.defaultSvg)(svg)).dataFromNote(makeNoteRdf)
              }

              val commentImage: Accu[Int => Turtle] =
                c.tree match {
                  case None =>
                    originalPart |+| (c.line.map(Container.liftZeileContainer) match {
                      case None          => Accu.empty
                      case Some(newRoot) =>
                        val svg = Svg.defaultSvg.drawAll(newRoot)
                        Accu.note(DrawnNotes.make(Svg.defaultSvg)(svg)).dataFromNote(makeNoteRdf)
                    })

                  case Some(ct) =>
                    val svg = new comment_tree.CommentTreeSvg(Svg.defaultSvg).draw(ct, Some(c), notes)
                    Accu.note(DrawnNotes.make(Svg.defaultSvg)(svg)).dataFromNote(makeNoteRdf)

                }

              metaData |+| commentImage
          })

        val globalComment = notes.globalComment.toList.map(ct => {
          val svg = new comment_tree.CommentTreeSvg(Svg.defaultSvg).draw(ct, None, notes)
          container |+| Accu.note(DrawnNotes.make(Svg.defaultSvg)(svg)).dataFromNote(makeNoteRdf)
        })

        val parts     = (globalComment ++ commentData.map(makeComment)).foldK.mapWithIndex((f, i) => f(i))
        val apparatus = Accu.data[Turtle](
          Statement(dataUri(doc.id), dataUri("documentCriticalApparatus") -> dataUri(collectionName))
        )

        parts |+| apparatus
      })

    def fullText(doc: Document, notes: RootContainer): URIO[Fence, Accu[Turtle]] =
      Fence.measurePure("docs-full-text")({
        val texts = Container.fold[List[String]] {
          case _: RootContainer                    => Nil
          case _: FormteilContainer                => Nil
          case _: MiscContainer                    => Nil
          case ParatextContainer(_, text, _, _, _) => List(text)
          case ZeileContainer(_, children)         =>
            children.collect({ case s: Syllable =>
              s.text
            })
        }(notes)

        val text = texts.flatten
          .mkString(" ")
          .replace("- ", "")
          .replaceAll("\\s+", " ")
          .pipe(Normalizer.normalize(_, Normalizer.Form.NFD))

        val normalizedText = texts.flatten
          .mkString("")
          .toLowerCase
          .replace("-", "")
          .replaceAll("\\s+", "")
          .pipe(Normalizer.normalize(_, Normalizer.Form.NFD))
          .replaceAll("[^\\p{ASCII}]", "")

        Accu.data(Statement(dataUri(doc.id), dataUri("fullText") -> LString(text))) |+|
          Accu.data(Statement(dataUri(doc.id), dataUri("fullTextNormalized") -> LString(normalizedText)))
      })

    def search(doc: Document, rc: RootContainer): URIO[Fence, Accu[Turtle]] =
      Fence.measurePure("docs-notes-search")({
        val notes   = Container.getAllNotes(rc)
        val pitches = notes
          .foldMap(n =>
            NoteStringifier
              .nameNote(n)
              .fold(Accu.logWarning[String](FromDoc(doc), s"Note out of range ${n.toString}"))(Accu.data[String]),
          )
          .combineData(_.mkString(""))
          .map(text => Statement(dataUri(doc.id), dataUri("pitches") -> LString(text)))

        val directions = NoteStringifier.directions(notes)
        val intervals  = NoteStringifier.intervals(notes)

        pitches
          .addData(Statement(dataUri(doc.id), dataUri("directions") -> LString(directions)))
          .addData(Statement(dataUri(doc.id), dataUri("intervals") -> LString(intervals)))
      })

    def svg(
        doc: Document,
        src: Source,
        notes: RootContainer
    ): URIO[ManifestStore & HttpClient & Fence, Accu[Turtle]] =

      val breakpoints = LazyList.iterate(480)(old => (old * 1.25).toInt).takeWhile(_ < 2500).toList
      val drawers     =
        breakpoints.map(width =>
          Svg(
            new CanvasConfig(
              embedFonts = false,
              useSystemFontNames = false,
              width = width.toDouble,
              height = width.toDouble * Math.sqrt(2)
            ).withComments
          ),
        )

      def getManifest = for manifest <-
          Fence.measure("docs-manifest")(
            doc.additionalData.get("iiifs").filter(!_.isBlank) match {
              case None        =>
                if src.manifest.isEmpty then ZIO.succeed(Accu.empty)
                else
                  Iiif
                    .manifestForDocument(src.manifest, doc.foliostart, src.foliooffset)
                    .fold(err => Accu.logWarning(FromDoc(doc), s"failed to retrieve manifest: $err"), Accu.data)
              case Some(iiifs) =>
                ZIO.succeed(
                  Iiif
                    .manifestFromIIIFJsonList(iiifs, doc.foliostart)
                    .fold(
                      err => Accu.logWarning(FromDoc(doc), s"failed to retrieve manifest: $err (inline json)"),
                      Accu.data
                    )
                )
            }
          )
      yield manifest

      def pages(raw: List[Pages.Page], manifest: Accu[Iiif.Manifest]) =
        val drawnPages = drawers.map(d => d -> d.drawPages(raw.map(_.notes))).toMap
        raw.map(page => {
          val notes = drawers
            .map(d =>
              (for
                bbs <- drawnPages get d
                bb  <- bbs lift page.idx
              yield Accu.note(DrawnNotes.make(d)(bb)))
                .getOrElse(
                  Accu.logWarning(FromDoc(doc), s"missing page ${page.idx} for drawer ${d.config.width}px")
                ),
            )
            .foldK

          manifest.data.headOption map (_ `imageInfoForPage` page.label match {
            case Left(err)  => notes.addData(page).logWarning(FromDoc(doc), err)
            case Right(uri) => notes.addData(page.copy(imageUri = uri))
          }) getOrElse notes.addData(page)
        })

      def rdf(pages: List[Accu[Pages.Page]]): List[Turtle] =
        val collectionName: String = "document_images_" + doc.id
        Statement(dataUri(doc.id), dataUri("documentNotes") -> dataUri(collectionName)) ::
          pages.filterNot(_.data.isEmpty).map { a =>
            val (notes, page) = (a.notes, a.data.head)
            Statement(
              dataUri(collectionName),
              viewUri("hasPage") -> Blank(
                viewUri("pageNr")         -> LInt(page.idx),
                viewUri("image")          -> LString(page.imageUri),
                label                     -> LString(page.label),
                viewUri("hasResolutions") -> Blank(
                  notes.map { notes =>
                    viewUri("resolution") -> Blank(
                      viewUri("url") -> LString(notes.hash + ".svg"),
                      viewUri("res") -> LInt(Math.ceil(notes.width).toInt)
                    )
                  }*
                )
              )
            )
          }

      for
        manifest   <- getManifest
        (err, raw) <- Fence.measurePure("docs-split-pages")(Pages.mkPages(notes, doc.foliostart).partitionMap(identity))
        failedPages = Accu.empty.logWarnings(FromDoc(doc), err)
        pages      <- Fence.measurePure("docs-render-pages")(pages(raw, manifest))
      yield Accu(
        manifest.log ++ failedPages.log ++ pages.toVector.flatMap(_.log),
        pages.toVector.flatMap(_.notes),
        rdf(pages).toVector
      )

    def pdf(doc: Document, notes: RootContainer): URIO[Fence & ContainerEngine, Accu[Turtle]] =
      Fence.measure("docs-render-print-view"):
        val printViewDrawer = Svg(
          new CanvasConfig(
            embedFonts = false,
            useSystemFontNames = true,
            width = 1280.0,
            height = 1280.0 * Math.sqrt(2)
          )
        )
        val printSettings   = de.olyro.monodi.exprt.print.Settings(pageNumberPrefix = None)
        val boxes           = Print(printViewDrawer, printSettings).drawPrintView(notes)
        boxes.flatMap: boxes =>
          if boxes.isEmpty then ZIO.succeed(Accu.empty)
          else
            (for
              pdf <- DrawnNotes.makePdf(printViewDrawer)(boxes)
              rdf  =
                Statement(dataUri(s"document_images_${doc.id}"), dataUri("hasPrintView") -> LString(s"${pdf.hash}.pdf"))
            yield Accu.note(pdf).addData(rdf))
              .catchAll(t => ZIO.succeed(Accu.logError(FromDoc(doc), t.toString)))

    (data: (Document, Option[Json], Source)) => {
      val (doc, rawNotes, src) = data
      (for
        _             <- ZIO.fail(Accu.empty[Turtle]).when(!filter.documentShouldBeExported(doc))
        (notes, evos) <- parseNotes(doc, rawNotes)
        makeNotes      = filter.notesShouldBeExportedFor(doc) && evos.keepInExport
        makeComments   = makeNotes && filter.apparatusShouldBeExportedFor(doc)
        warnings       = evos.messages.map(Message(Message.Warning, Message.FromDoc(doc), _))
        metaTTL       <- mainRdf(doc) |> fullText(doc, notes) |> search(doc, notes)
        notesTTL      <- if makeNotes then svg(doc, src, notes) ||> pdf(doc, notes) else ZIO.succeed(Accu.empty[Turtle])
        commentsTTL   <- if makeComments then comments(doc, notes) else ZIO.succeed(Accu.empty[Turtle])
        _             <- ZIO.foreach(warnings ++ metaTTL.log ++ notesTTL.log ++ commentsTTL.log)(msg => printLine(msg.print).orDie)
      yield DocStage(
        doc = Some(doc),
        onlyTtl = metaTTL,
        withNotes = metaTTL |+| notesTTL,
        withNotesAndCriticalApparatus = metaTTL |+| notesTTL |+| commentsTTL,
        commonWarnings = warnings
      ))
        .mapError(err => {
          DocStage(None, Accu.empty, Accu.empty, Accu.empty, err.log.toList)
        })
        .merge
        .onTermination(c => (printLine(doc.id) *> printLine(c.prettyPrint)).orDie)
        .sandbox
        .orElseSucceed(DocStage(None, Accu.empty, Accu.empty, Accu.empty, List()))
    }

  def alternativeSortingAttributes: URIO[Fence & DBDoobie, Accu[Turtle]] =
    final case class Attr(getter: Document => String, attr: String)
    val attributes = List(
      Attr(_.dokumenten_id, "documentDokumenten_idA"),
      Attr(_.bibliographischerverweis, "documentBibliographischerverweisA"),
      Attr(_.additionalData.getOrElse("Melodiennummer_Katalog", ""), "Melodiennummer_KatalogA")
    )

    def makeFor(documents: Iterable[Document], attr: Attr): List[Turtle] =
      val digits = "[0-9]+".r

      def toLength(dokumenten_id: String): List[Int] =
        digits.findAllMatchIn(dokumenten_id).map(mtch => mtch.end - mtch.start).toList

      def listMax(l1: List[Int], l2: List[Int]): List[Int] =
        (l1, l2) match
          case (Nil, l2)            => l2
          case (l1, Nil)            => l1
          case (h1 :: t1, h2 :: t2) => Math.max(h1, h2) :: listMax(t1, t2)

      val indices = documents.foldLeft(List.empty[Int])((max, doc) => listMax(max, toLength(attr.getter(doc))))

      def toRdf(uri: String, propertyName: String, text: String, maxDigits: List[Int]): Turtle =
        var index = 0
        val newId = digits.replaceAllIn(
          text,
          mtch => {
            val length         = mtch.end - mtch.start
            val expectedLenght = maxDigits.lift(index).getOrElse(length)
            val prefix         = "0" * (expectedLenght - length)
            index += 1
            prefix + mtch.toString
          }
        )
        Statement(dataUri(uri), dataUri(propertyName) -> LString(newId))

      documents.map(doc => toRdf(doc.id, attr.attr, attr.getter(doc), indices)).toList

    Fence.measure("make-alt-sorting")(
      db.DBRunner
        .runZ(db.DBRunner.listDocuments)
        .orDie
        .map(all => attributes.flatMap(makeFor(all, _)))
        .map(Accu.allData)
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
          (zip =<< toBytes(doc) -> List(doc.quelle_id, docId, "meta.json")).when(filter.documentShouldBeExported(doc))
        notes <- db.DBRunner.runZ(db.DBRunner.getDocumentNotes(docId)).orDie
        _     <- ZIO
                   .foreachDiscard(notes)(n => zip =<< toBytes(n) -> List(doc.quelle_id, docId, "data.json"))
                   .when(filter.notesShouldBeExportedFor(doc))
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
