package de.olyro.monodi
package exprt
package print
package bookbinding

import zio.*
import zio.interop.toEffect
import cats.effect as E
import java.nio.file.*
import de.olyro.monodi.data.*
import de.olyro.monodi.db.DBRunner
import de.olyro.monodi.db.DBRunner.DBDoobie
import de.olyro.monodi.data.notes.RootContainer
import doobie.util.transactor.Transactor
import Size.*

import cover as C
import hierarchy as H
import cats.data.NonEmptyList
import de.olyro.monodi.data.notes.Justification
import H.TableOfContentsItem

class Bookbinder(root: Path, state: Ref[State]):
  def queue(request: Request): UIO[Option[WorkId]] =
    for
      workId      <- ZIO.succeed(WorkId.random())
      enoughSpace <- state.modify(_.enqueue(workId, request))
    yield if enoughSpace then Some(workId) else None

  def getStatusAndMaybeRemove(id: WorkId): UIO[Status] =
    state.get
      .map(_.getStatus(id))
      .flatMap:
        case Some(status) => ZIO.succeed(status)
        case None         =>
          ZIO
            .attemptBlocking(Files.readAllBytes(root.resolve(id.toString)))
            .option
            .flatMap:
              case None        => ZIO.succeed(Status.NotFound)
              case Some(bytes) =>
                ZIO
                  .attemptBlocking(Files.delete(root.resolve(id.toString)))
                  .as(Status.Done(BinaryData(bytes)))
      .orDie

  def abort(id: WorkId): UIO[Unit] =
    for
      fiberO <- state.modify(_.abort(id))
      _      <- ZIO.foreach(fiberO)(_.interrupt)
    yield ()

object Bookbinder:
  private def runLoop(
      root: Path,
      state: Ref[State],
      exportConfig: ExportConfig
  ): ZIO[ContainerEngine & DBDoobie, Throwable, Unit] =
    state
      .modify(_.popFromQueue)
      .delay(2.seconds)
      .flatMap:
        case None                    => runLoop(root, state, exportConfig)
        case Some((workId, request)) =>
          (for
            childStartSignal <- Promise.make[Nothing, Unit]
            report            = (p: Progress) => state.update(_.reportProgress(workId, p)) // .delay(200.millis)
            workFiber        <- handleSingleRequest(root, workId, request, childStartSignal, exportConfig)
                                  .provideSome[DBDoobie & ContainerEngine](ProgressNotifier.fromCallback(report))
                                  .fork
            _                <- state.update(_.copy(inProgress = Some(WorkState(workId, Progress.StartingUp, workFiber))))
            _                <- childStartSignal.succeed(())
            childExit        <- workFiber.await
            _                <- ZIO.succeed(println(s"Child exited with $childExit"))
            _                <- state.update(_.copy(inProgress = None))
          yield ()) *> runLoop(root, state, exportConfig)

  private def handleSingleRequest(
      root: Path,
      workId: WorkId,
      request: Request,
      startSignal: Promise[Nothing, Unit],
      exportConfig: ExportConfig
  ): ZIO[ContainerEngine & DBDoobie & ProgressNotifier, Throwable, Unit] =
    loadDocuments(request.documents).flatMap({
      case Nil     => ZIO.unit
      case d :: ds =>
        handleSingleRequest(root, workId, NonEmptyList(d, ds), request, startSignal, exportConfig)
    })

  private def handleSingleRequest(
      root: Path,
      workId: WorkId,
      docs: NonEmptyList[DocWithSource],
      request: Request,
      startSignal: Promise[Nothing, Unit],
      exportConfig: ExportConfig
  ): ZIO[ContainerEngine & DBDoobie & ProgressNotifier, Throwable, Unit] =
    val path          = root.resolve(workId.toString)
    val printSettings = request.printSettings

    for
      _                <- startSignal.await
      defaultDocData    = DefaultDocData.gather(docs.toList, exportConfig.editorPseudonyms, exportConfig.editorNames)
      bookEntries       = getBookEntries(docs, defaultDocData, request)
      printViewRenderer = Svg(printSettings.canvasSettings)
      printViewLayouter = Print(printViewRenderer, getSettingsForPrint(printSettings, defaultDocData))
      boxes            <- printViewLayouter.drawPrintView(bookEntries)
      svgStrings       <- makeSvgStrings(boxes, printViewRenderer)
      pdfData          <- makePdf(svgStrings, printSettings)
      _                <- ZIO.attemptBlocking(Files.write(path, pdfData))
      _                <- ZIO.attemptBlocking(Files.deleteIfExists(path)).delay(5.minutes).forkDaemon
    yield ()

  private def getSettingsForPrint(printSettings: PrintSettings, defaultDocData: Option[DefaultDocData]): Settings =
    (printSettings.drawPageNumbers, defaultDocData) match
      case (false, _)       => Settings(None)
      case (true, None)     => Settings(Some("Seite"))
      case (true, Some(dd)) =>
        Settings(
          Some(
            s"${dd.genre} | ${dd.libLocation}, ${dd.libName}, ${dd.libSig} | ${dd.century} | ${dd.editorCode} | Seite"
          )
        )

  private def getBookEntries(
      docs: NonEmptyList[DocWithSource],
      defaultDocData: Option[DefaultDocData],
      request: Request
  ): List[BookEntry] =
    val allDiscriminators = NonEmptyList.of[DocWithSourceDiscriminator](
      DocWithSourceDiscriminator.dokumenteId
    )

    val customCover = request.customCover match
      case None                                   => None
      case Some(CustomCover.LineItemCover(items)) => Some(BookEntry.Cover(C.Cover.TextCover(items, Nil)))

    val sourceDescriptions: List[BookEntry.SourceDescription] = docs.toList
      .map(_.source)
      .distinctBy(_.id)
      .map[BookEntry.SourceDescription](BookEntry.SourceDescription(_))
      .filter(_ => request.printSettings.addSourceDescriptions)

    val criticalApparati: List[BookEntry.CriticalApparatus] =
      if request.printSettings.addCriticalApparatus then
        docs.toList
          .filter(_.notes.comments.nonEmpty)
          .map(d => BookEntry.CriticalApparatus(d.doc.id, d.doc.dokumenten_id, d.notes))
      else Nil

    val cover = customCover match
      case Some(cc) => cc
      case None     =>
        defaultDocData match
          case None              => BookEntry.Cover(C.Cover.TextCover(defaultCover.topItems ++ betaLines, defaultCover.bottomItems))
          case Some(defaultData) => BookEntry.Cover(getAutoCover(defaultData))

    val numKindOfEntries = List(
      true, // we always render notes
      request.printSettings.addSourceDescriptions,
      request.printSettings.addCriticalApparatus
    ).count(_ == true)

    val docHierarchy = docs.map: d =>
      H.TableOfContentsItem(d.doc.dokumenten_id, Some(H.TocEntityId.Document(d.doc.id)), Nil)
    val bookEntries  = makeMetaAndDocEntries(docs.toList, allDiscriminators)
    val toc          =
      if numKindOfEntries == 1 then BookEntry.TableOfContents(H.TableOfContents(docHierarchy.toList).toView.items)
      else
        BookEntry.TableOfContents:
          H.TableOfContents(
            List(
              TableOfContentsItem("Dokumente", None, docHierarchy.toList),
              TableOfContentsItem(
                "Quellenbeschreibungen",
                None,
                sourceDescriptions.map(sd =>
                  TableOfContentsItem(
                    sd.source.id,
                    Some(H.TocEntityId.SourceDescription(sd.source.id)),
                    Nil
                  )
                )
              ),
              TableOfContentsItem(
                "Kritischer Apparat",
                None,
                criticalApparati.map(c =>
                  TableOfContentsItem(
                    c.documentId,
                    Some(H.TocEntityId.CriticalApparatus(c.documentId)),
                    Nil
                  )
                )
              )
            )
          ).toView
            .items

    cover :: List(toc).filter(_ =>
      request.printSettings.drawTableOfContents
    ) ++ bookEntries ++ sourceDescriptions ++ criticalApparati

  private def getAutoCover(dd: DefaultDocData): C.Cover =
    val libText         = s"${dd.libLocation}, ${dd.libName}, ${dd.libSig}"
    val additionalLines =
      List(
        LineItem.VSkip(3.0.percentPH),
        LineItem.Text(s"${dd.genre} aus", 4.0.percentPW),
        LineItem.Text(libText, 4.0.percentPW),
        LineItem.VSkip(3.0.percentPH),
        LineItem.Text("herausgegeben von", 2.5.percentPW),
        LineItem.VSkip(3.0.percentPH),
        LineItem.Text(dd.editorName, 2.5.percentPW),
        LineItem.VSkip(5.0.percentPH)
      ) ++ betaLines

    C.Cover.TextCover(defaultCover.topItems ++ additionalLines, defaultCover.bottomItems)

  private def loadDocuments(ids: List[String]): ZIO[DBDoobie & ProgressNotifier, Throwable, List[DocWithSource]] =
    for
      sources <- Ref.make(Map.empty[String, Source])
      docs    <- ZIO.foreach(ids.zipWithIndex): (id, idx) =>
                   for
                     _      <- ProgressNotifier.notify(Progress.LoadingNotes(idx))
                     doc    <- loadDocumentMeta(id)
                     notes  <- loadDocumentNotes(id)
                     source <- sources.get.flatMap(_.get(doc.quelle_id) match {
                                 case Some(s) => ZIO.succeed(s)
                                 case None    => DBRunner.runZ(DBRunner.getSource(doc.quelle_id)).map(_.get)
                               })
                     _      <- sources.update(_.updated(doc.quelle_id, source))
                   yield DocWithSource(doc, notes, source)
    yield docs

  private def makeMetaAndDocEntries(
      docs: List[DocWithSource],
      metaDiscriminators: NonEmptyList[DocWithSourceDiscriminator]
  ): List[BookEntry] =

    def makeForItem(i: DocWithSource): List[LineItem] =
      val sizes = 1.2.ofSFS #:: LazyList.continually(1.1.ofSFS)
      val texts = metaDiscriminators.map(disc => disc.valueGetter(i)).toList
      metaDiscriminators.toList
        .zip(texts)
        .zip(sizes)
        .flatMap({ case ((disc, text), size) =>
          val item = LineItem.Text(text, size, None, Some(Justification.Right))
          if disc.vskipAfter then List(item, LineItem.VSkip(0.05.ofPH))
          else List(item)
        })

    def dropRepeated(last: List[LineItem], current: List[LineItem]): List[LineItem] =
      def go(last: List[LineItem], current: List[LineItem], vskip: Option[LineItem]): List[LineItem] =
        (last, current) match
          case (Nil, _)                                                        => vskip.toList ++ current
          case (_, Nil)                                                        => Nil
          case ((l @ LineItem.VSkip(_)) :: last, LineItem.VSkip(_) :: current) => go(last, current, Some(l))
          case (_ @LineItem.VSkip(_) :: _, _)                                  => sys.error("Should never happen")
          case (_, _ @LineItem.VSkip(_) :: _)                                  => sys.error("Should never happen")
          case ((l: LineItem.Text) :: last, (c: LineItem.Text) :: current)     =>
            if l.text == c.text then go(last, current, vskip)
            else vskip.toList ++ (c :: go(last, current, None))

      go(last, current, None)

    def go(
        lastFullMeta: List[LineItem],
        docs: List[DocWithSource],
        accu: List[BookEntry]
    ): List[BookEntry] =
      docs match
        case Nil             => accu.reverse
        case docItem :: docs =>
          val fullMeta  = makeForItem(docItem)
          val meta      = dropRepeated(lastFullMeta, fullMeta)
          val metaEntry = if meta.isEmpty then Nil else List(BookEntry.MetaData(meta))
          val bookEntry =
            if docItem.doc.editionsstatus == "zitiert" then notYetSupportedCover
            else BookEntry.Document(docItem.doc.id, docItem.notes)

          go(fullMeta, docs, (bookEntry :: metaEntry) ++ accu)

    go(Nil, docs, Nil)

  private def loadDocumentMeta(id: String): ZIO[DBDoobie, Throwable, Document] =
    DBRunner
      .runZ(DBRunner.getDocument(fixId(id)))
      .someOrFail(new Exception(s"Document $id not found"))

  private def loadDocumentNotes(id: String): ZIO[DBDoobie, Throwable, RootContainer] =
    DBRunner
      .runZ(DBRunner.getDocumentNotesDecoded(fixId(id)))
      .someOrFail(new Exception(s"Notes for document $id not found"))
      .map(_._1)

  private def makeSvgStrings(pages: List[Page], renderer: Svg): URIO[ProgressNotifier, List[String]] =
    ZIO.foreach(pages.zipWithIndex): (page, idx) =>
      ProgressNotifier.notify(Progress.BoxToSvg(idx + 1)) *> ZIO.succeed(
        renderer.makeSVG(page.content, page.additionalCss.getOrElse(""))
      )

  private def makePdf(
      svgStrings: List[String],
      settings: PrintSettings
  ): ZIO[ContainerEngine & ProgressNotifier, Throwable, Array[Byte]] =
    val pdfPrinter = new Pdf(
      Pdf.Config(
        marginTop = settings.marginTopInMm.toInt,
        marginLeft = settings.marginLeftInMm.toInt,
        marginRight = settings.marginRightInMm.toInt,
        paperWidth = settings.paperWidthInMm,
        paperHeight = settings.paperHeightInMm
      )
    )

    ProgressNotifier.notify(Progress.ConvertingSvgToPdf) *>
      pdfPrinter.printSvg(svgStrings).map(_.data)

  val live: ZLayer[ContainerEngine & DBDoobie, Throwable, Bookbinder] = ZLayer.scoped:
    for
      ref  <- Ref.make(State.empty)
      conf <- ExportConfig.load
      root <- Util.createCleanTempDir("monodi-bookbinder")
      _    <- runLoop(root, ref, conf).forkScoped
      bb    = Bookbinder(root, ref)
    yield bb

  val liveResource: E.Resource[E.IO, Bookbinder] =
    given Runtime[Any] = Runtime.default

    val acquire: E.IO[Scope.Closeable] = toEffect(
      Scope.make
    )

    def release(scope: Scope.Closeable): E.IO[Unit] = toEffect(
      scope.close(Exit.succeed(()))
    )

    def createBookbinder(scope: Scope.Closeable): E.IO[Bookbinder] = toEffect(
      (ContainerEngine.live ++ DBRunner.live)
        .to(live)
        .build(scope)
        .map(_.get)
    )

    E.Resource.make(acquire)(release).flatMap(scope => createBookbinder(scope).toResource)

  private lazy val defaultCover: C.Cover.TextCover =
    import Size.*
    C.Cover.TextCover(
      topItems = List(
        LineItem.VSkip(8.0.percentPH),
        LineItem.Text("CORPUS MONODICUM", 4.5.percentPH, "#a61c00"),
        LineItem.VSkip(2.0.percentPH),
        LineItem.Text("Die einstimmige Musik", 4.5.percentPW, "#a61c00"),
        LineItem.Text("des lateinischen Mittelalters", 4.5.percentPW, "#a61c00"),
        LineItem.VSkip(3.0.percentPH),
        LineItem.Text("Im Auftrag der Akademie der Wissenschaften und der Literatur | Mainz", 2.5.percentPW),
        LineItem.Text("am Zentrum für Philologie und Digitalität der Universität Würzburg", 2.5.percentPW),
        LineItem.VSkip(3.0.percentPH),
        LineItem.Text("unter Leitung von", 2.5.percentPW),
        LineItem.VSkip(3.0.percentPH),
        LineItem.Text("Andreas Haug und Frank Puppe", 2.5.percentPW)
      ),
      bottomItems = List(
        "Open Access © 2024 bei CORPUS MONODICUM https://corpus.monodicum.de Dieses Werk ist",
        "lizenziert unter einer Creative Commons Namensnennung – Nicht kommerziell – Keine",
        "Bearbeitung 4.0 International Lizenz. http://doi.org/10.1515/ etc."
      ).map(text => LineItem.Text(text, 2.0.percentPW))
    )

  private lazy val betaLines = List(
    LineItem.VSkip(5.0.percentPH),
    LineItem.Text("Auszug aus der Beta-Version 1.0 der digitalen Ausgabe (2024)", 2.5.percentPW)
  )

  private lazy val notYetSupportedCover: BookEntry =
    import Size.*
    BookEntry.Cover(
      C.Cover.TextCover(
        topItems = List(
          LineItem.VSkip(8.0.percentPH),
          LineItem.Text("Entschuldigung", 4.5.percentPH, "#a61c00"),
          LineItem.VSkip(8.0.percentPH),
          LineItem.Text("Dokumente aus dem", 4.5.percentPW, "#a61c00"),
          LineItem.Text("Graduale Synopticum", 4.5.percentPW, "#a61c00"),
          LineItem.Text("stehen im Druck leider", 4.5.percentPW, "#a61c00"),
          LineItem.Text("noch nicht zur Verfügung", 4.5.percentPW, "#a61c00")
        ),
        bottomItems = Nil
      )
    )

  // there are some invalid ids of multiple lines which are not supported. This is a quick fix
  // that doesn't hurt because all ids should be UUIDs and not contain "%0A" anyway, but
  // it should work until the ids get fixed in the underlying data base
  private def fixId(s: String): String =
    s.replace("%0A", "\n")
