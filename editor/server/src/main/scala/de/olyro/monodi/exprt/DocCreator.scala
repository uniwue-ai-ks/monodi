package de.olyro.monodi
package exprt

import cats.implicits.*
import de.olyro.monodi.Util.http.HttpClient
import de.olyro.monodi.Util.Fence
import de.olyro.monodi.exprt.iiif.Iiif
import Iiif.ManifestStore
import de.olyro.monodi.data.notes.*
import de.olyro.monodi.data.{Document, Source}
import de.olyro.monodi.db.DBRunner
import de.olyro.monodi.exprt.Message.{Source as _, *}
import de.olyro.monodi.exprt.Turtle.*
import io.circe.Json
import scalatags.text.Frag
import filter.ExportFilter
import filter.DocPublishInfo

import zio.{Ref as _, *}

import java.text.Normalizer
import scala.util.chaining.*
import de.olyro.monodi.exprt.print.Print
import zio.Console.printLine
import java.util.UUID
import cats.data.NonEmptyList

class DocCreator(pseudonyms: Map[String, String], filter: ExportFilter):
  import Export.DocStage
  import Export.dataUri
  import Export.viewUri
  import Export.a
  import Export.label

  def createNormalDocument(
      doc: Document,
      rawNotes: Option[Json],
      src: Source
  ): zio.ZIO[Iiif.StoreService & HttpClient & Fence & ContainerEngine, Nothing, DocStage] =
    (for
      _             <- printLine(s"processing document ${doc.id}/${doc.dokumenten_id}...").orDie
      (notes, evos) <- parseNotes(doc, rawNotes)
      pubInfo        = DocPublishInfo.fromDoc(doc)
      makeNotes      = filter.notesShouldBeExportedFor(pubInfo) && evos.keepInExport
      makeComments   = makeNotes && filter.apparatusShouldBeExportedFor(pubInfo)
      warnings       = evos.messages.map(Message(Message.Warning, Message.FromDoc(doc), _))
      textInitiaList = if doc.gattung1 == "Tropus" then TextInitia.getTextInitia(notes) else Nil
      textInitiaJoined = textInitiaList.mkString("#")
      metaTTL       <-
        mainRdf(doc, getSpielType(doc), textInitiaJoined) |> fullText(doc, List(notes)) |> search(doc, List(notes))
      notesTTL      <- if makeNotes then svg(doc, src, List(notes)) ||> pdf(doc, notes) else ZIO.succeed(Accu.empty[Turtle])
      commentsTTL   <- if makeComments then comments(doc, List(notes)) else ZIO.succeed(Accu.empty[Turtle])
      _             <- ZIO.foreach(warnings ++ metaTTL.log ++ notesTTL.log ++ commentsTTL.log)(msg => printLine(msg.print).orDie)
    yield DocStage(
      doc = Some(doc),
      onlyTtl = metaTTL,
      withNotes = metaTTL |+| notesTTL,
      withNotesAndCriticalApparatus = metaTTL |+| notesTTL |+| commentsTTL,
      commonWarnings = warnings,
      derivedCategoryValues = if textInitiaList.nonEmpty then Map("textInitia" -> textInitiaList) else Map.empty
    ))
      .mapError(err => {
        DocStage(None, Accu.empty, Accu.empty, Accu.empty, err.log.toList, Map.empty)
      })
      .merge
      .onTermination(c => (printLine(doc.id) *> printLine(c.prettyPrint)).orDie)
      .sandbox
      .orElseSucceed(DocStage(None, Accu.empty, Accu.empty, Accu.empty, List(), Map.empty))

  def createSpiel(
      ref: String,
      docs: List[(Document, Option[Json], Source)]
  ): zio.ZIO[Iiif.StoreService & HttpClient & Fence & ContainerEngine, Nothing, DocStage] =
    import Accu.*

    val sortedDocs = sortByDokumentenId(docs, _._1.dokumenten_id)
    println(s"creating spiel for $ref with: ")
    println(sortedDocs.map("  - " + _._1.dokumenten_id).mkString("\n"))

    sortedDocs.headOption match
      case None                     =>
        printLine(s"not creating spiel for $ref").orDie.as(DocStage(None, Accu.empty, Accu.empty, Accu.empty, Nil, Map.empty))
      case Some((oldHeadDoc, _, _)) =>
        val headDoc = oldHeadDoc.copy(
          id = UUID.randomUUID().toString,
          dokumenten_id = ref,
          gattung1 = "Spiel"
        )

        val prog = for
          _            <- printLine(s"creating spiel for $ref").orDie
          loaded       <- loadSpielItems(sortedDocs)
          rootsWithDocs = loaded.data.map((d, rc, _) => (rc, d)).toList
          _            <- ZIO.fail(Accu.logWarning(FromDoc(oldHeadDoc), "no documents could be loaded")).when(loaded.data.isEmpty)
          pubInfo       = DocPublishInfo.fromDoc(headDoc)
          makeNotes     = filter.notesShouldBeExportedFor(pubInfo)
          makeComments  = makeNotes && filter.apparatusShouldBeExportedFor(pubInfo)
          metaTTL      <-
            mainRdf(headDoc, SpielType.Spiel, "") |> fullText(headDoc, rootsWithDocs.map(_._1)) |> search(
              headDoc,
              rootsWithDocs.map(_._1)
            )
          notesTTL     <-
            if makeNotes then svgWithMetadata(headDoc, loaded.data.head._3, rootsWithDocs) else ZIO.succeed(Accu.empty)
          commentsTTL  <- if makeComments then comments(headDoc, rootsWithDocs.map(_._1)) else ZIO.succeed(Accu.empty)
        yield DocStage(
          doc = Some(headDoc),
          onlyTtl = metaTTL,
          withNotes = metaTTL |+| notesTTL,
          withNotesAndCriticalApparatus = metaTTL |+| notesTTL |+| commentsTTL,
          loaded.log.toList,
          Map.empty
        )

        prog
          .mapError(err => {
            DocStage(None, Accu.empty, Accu.empty, Accu.empty, err.log.toList, Map.empty)
          })
          .merge
          .onTermination(c => (printLine(oldHeadDoc.id) *> printLine(c.prettyPrint)).orDie)
          .sandbox
          .orElseSucceed(DocStage(None, Accu.empty, Accu.empty, Accu.empty, List(), Map.empty))

  /**
   * This method sorts elements by their dokumenten id, i.e. something like
   * Civ 101-86-21. It should correctly handle text, numbers and recto/verso.
   */
  private def sortByDokumentenId[A](as: List[A], f: A => String): List[A] =
    as.sortBy(a => LexicographicMapper.makeLexicographicallySortable(f(a)))

  private def parseNotes(
      doc: Document,
      rawNotes: Option[Json]
  ): ZIO[Fence, Accu[Nothing], (RootContainer, Evolutions.EvolutionResult)] = Fence.measure("docs-evolve")(
    ZIO
      .fromEither(rawNotes.toRight("could not load notes"))
      .map(j => Container.parse(j.printWith(DBRunner.jsonPrinter)))
      .absolve
      .mapError(Accu.logWarning(Message.FromDoc(doc), _))
  )

  private def loadSpielItems(
      items: List[(Document, Option[Json], Source)]
  ): ZIO[Fence, Nothing, Accu[(Document, RootContainer, Source)]] =
    ZIO
      .foreach(items): (doc, rawNotes, src) =>
        parseNotes(doc, rawNotes).either.map:
          case Left(accu)      =>
            println("failed to parse notes for document " + doc.dokumenten_id)
            accu
          case Right((rc, rs)) =>
            if rs.keepInExport then Accu.data((doc, rc, src))
            else
              println("excluding document " + doc.dokumenten_id + " by evolution rules")
              Accu.logWarning(Message.FromDoc(doc), "document excluded by evolution rules")
      .map: all =>
        all.foldK

  /**
   * Extracts the full text from the notes. Can be given multiple notes,
   * for example to create a single spiel out of multiple documents,
   * but always take the metadata/dataUri from the doc document.
   */
  private def fullText(doc: Document, notes: List[RootContainer]): URIO[Fence, Accu[Turtle]] =
    Fence.measurePure("docs-full-text")({
      val texts = notes.flatMap: notes =>
        Container.fold[List[String]] {
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

  /**
   * Extracts musical information from the notes and converts them to text to
   * make them available for search.
   *
   * Can be given multiple notes, for example to create a single spiel
   * out of multiple documents, but always take the metadata/dataUri
   * from the doc document.
   */
  private def search(doc: Document, rcs: List[RootContainer]): URIO[Fence, Accu[Turtle]] =
    Fence.measurePure("docs-notes-search")({
      val notes   = rcs.flatMap(Container.getAllNotes)
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

  private def svg(
      doc: Document,
      src: Source,
      roots: List[RootContainer]
  ): URIO[ManifestStore & HttpClient & Fence, Accu[Turtle]] =
    svgWithMetadata(doc, src, roots.map((_, doc)))

  private def svgWithMetadata(
      doc: Document,
      src: Source,
      rootsWithDocs: List[(RootContainer, Document)]
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
      val pagesRDF               = pages.filterNot(_.data.isEmpty).flatMap { a =>
        val (notes, page) = (a.notes, a.data.head)
        val metadata      = metadataForPage(collectionName, page)
        val metadataLink  = metadata.toList.map: md =>
          (viewUri("hasMetadata") -> md.node)

        val basePageData = List(
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
        ) ++ metadataLink

        Statement(dataUri(collectionName), viewUri("hasPage") -> Blank(basePageData*)) :: metadata.toList
          .map(_.statement)
      }
      Statement(dataUri(doc.id), dataUri("documentNotes") -> dataUri(collectionName)) :: pagesRDF

    def splitPagesWithMetadata(
        rootsWithDocs: List[(RootContainer, Document)]
    ): (List[String], List[Pages.Page]) =
      val result = rootsWithDocs.foldLeft(Vector.empty[Either[String, Pages.Page]]) { case (accu, (rc, chantDoc)) =>
        val newIndex = accu
          .collect:
            case Right(p) => p.idx + 1
          .maxOption
          .getOrElse(0)

        val pages    = Pages.mkPages(rc, chantDoc.foliostart, newIndex)
        val metadata = Pages.SpielElementMetadata(
          chantDoc.dokumenten_id,
          chantDoc.gattung1,
          chantDoc.gattung2,
          chantDoc.bibliographischerverweis
        )

        // Attach metadata to the first page
        pages match
          case Right(firstPage) +: rest =>
            (accu :+ Right(firstPage.copy(chantMetadata = Some(metadata)))) ++ rest
          case other                    =>
            accu ++ other
      }

      val (errors, pages) = result.partitionMap(identity)
      (errors.toList, pages.toList)

    for
      manifest   <- getManifest
      (err, raw) <- Fence.measurePure("docs-split-pages")(splitPagesWithMetadata(rootsWithDocs))
      failedPages = Accu.empty.logWarnings(FromDoc(doc), err.toList)
      pages      <- Fence.measurePure("docs-render-pages")(pages(raw.toList, manifest))
    yield Accu(
      manifest.log ++ failedPages.log ++ pages.toVector.flatMap(_.log),
      pages.toVector.flatMap(_.notes),
      rdf(pages).toVector
    )

  /**
   * Generates comments as HTML and SVG and links them.
   *
   * Can be given multiple notes, for example to create a single spiel
   * out of multiple documents, but always take the metadata/dataUri
   * from the doc document.
   */
  private def comments(doc: Document, roots: List[RootContainer]): URIO[Fence, Accu[Turtle]] =
    Fence.measurePure("docs-comments")({
      import scalatags.Text.all.{Frag as _, p as _, *}

      val collectionName = "htmlImageCollection_" + doc.id
      val commentData    = roots.map(root => (root, ViewModel.getCommentData(root)))

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

      def makeParatext(root: RootContainer, p: ParatextComment): Accu[Int => Turtle] = {
        p.tree match {
          case None       => Accu.empty
          case Some(tree) =>
            val svg = new comment_tree.CommentTreeSvg(Svg.defaultSvg).draw(tree, None, root)
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

      def makeComment(r: RootContainer, cd: CommentData): Accu[Int => Turtle] =
        cd.comment match
          case Left(pc) =>
            val accu = makeParatext(r, pc)
            if accu.data.isEmpty then Accu.empty
            else makeIntro(cd) |+| accu
          case Right(c) =>
            c.tree match
              case None     => Accu.empty
              case Some(ct) =>
                val svg = new comment_tree.CommentTreeSvg(Svg.defaultSvg).draw(ct, Some(c), r)
                makeIntro(cd) |+| Accu.note(DrawnNotes.make(Svg.defaultSvg)(svg)).dataFromNote(makeNoteRdf)

      val globalComment = roots
        .flatMap: notes =>
          notes.globalComment.map(gc => (notes, gc))
        .map((notes, gc) => {
          val svg = new comment_tree.CommentTreeSvg(Svg.defaultSvg).draw(gc, None, notes)
          container |+| Accu.note(DrawnNotes.make(Svg.defaultSvg)(svg)).dataFromNote(makeNoteRdf)
        })

      val parts     =
        (globalComment ++ commentData.flatMap((r, ct) => ct.map(ct => (r, ct))).map(makeComment)).foldK.mapWithIndex(
          (f, i) => f(i)
        )
      val apparatus = Accu.data[Turtle](
        Statement(dataUri(doc.id), dataUri("documentCriticalApparatus") -> dataUri(collectionName))
      )

      parts |+| apparatus
    })

  private def metadataForPage(collectionName: String, page: Pages.Page): Option[(node: Ref, statement: Statement)] =
    page.chantMetadata.flatMap: meta =>
      val metadataNode = dataUri(s"${collectionName}_page_${page.idx}_metadata")

      type MetadataRow = (index: Int, labelDe: String, labelEn: String, labelFr: String, value: String)
      val metadataEntries = List[MetadataRow](
        (0, "Dokumenten-ID", "Document ID", "ID du document", meta.dokumentenId),
        (1, "Gattung1", "Genre 1", "Genre 1", meta.gattung1),
        (2, "Gattung2", "Genre 2", "Genre 2", meta.gattung2),
        (3, "Nachweis Text", "Reference, text", "Référence, texte", meta.nachweisText)
      ).filter(_.value.nonEmpty).map { row =>
        viewUri("metadataEntry") -> Blank(
          viewUri("index") -> LInt(row.index),
          viewUri("label") -> LString(row.labelDe, Some("de")),
          viewUri("label") -> LString(row.labelEn, Some("en")),
          viewUri("label") -> LString(row.labelFr, Some("fr")),
          viewUri("value") -> LString(row.value)
        )
      }

      NonEmptyList
        .fromList(metadataEntries)
        .map: metadataEntries =>
          (
            node = metadataNode,
            statement = Statement(
              metadataNode,
              metadataEntries
            )
          )

  /** Classifies the documentes into three classes.
   *  - Element: an element of a Spiel
   *  - Spiel: a collection of elements (i.e. a synthetic document)
   *  - NormalDocument: a normal document, not part of a Spiel in any way
   */
  enum SpielType derives CanEqual:
    case Element
    case Spiel
    case NormalDocument

  private def mainRdf(doc: Document, spielType: SpielType, textInitia: String): URIO[Fence, Accu[Turtle]] =
    Fence.measurePure("docs-main-rdf")(Accu.data({

      /**
       * The string is chosen so that elements only match if `Element` is selected,
       * Spiel only match if `Spiel` is selected, and normal documents match
       * both since we filter for substrings, and `Spiel,Element` contains both.
       */
      val spielTypeString = spielType match
        case SpielType.Element        => "Element"
        case SpielType.Spiel          => "Spiel"
        case SpielType.NormalDocument => "Spiel,Element"

      val textInitiaArgs =
        if textInitia.nonEmpty then List(dataUri("textInitia") -> LString(textInitia))
        else Nil

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
        dataUri("documentEditionsstatus")           -> LString(doc.editionsstatus),
        dataUri("spielOrPassus")                    -> LString(spielTypeString),
        dataUri("spielOrPassus")                    -> LString(spielTypeString)
      ) ++ List[(Document => String, String)](
        (_.dokumenten_id, "documentDokumenten_idA"),
        (_.bibliographischerverweis, "documentBibliographischerverweisA"),
        (_.additionalData.getOrElse("Melodiennummer_Katalog", ""), "Melodiennummer_KatalogA")
      ).map: (getter, attribute) =>
        dataUri(attribute) -> LString(LexicographicMapper.makeLexicographicallySortable(getter(doc)))

      val additionalArgs = doc.additionalData.toList.map({ case (key, value) =>
        dataUri(key) -> LString(pseudonyms.getOrElse(value.trim, value))
      })

      Statement(
        dataUri(doc.id),
        a -> dataUri("document"),
        (mainArgs ++ additionalArgs ++ textInitiaArgs)*
      )
    }))

  private def pdf(doc: Document, notes: RootContainer): URIO[Fence & ContainerEngine, Accu[Turtle]] =
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

  /**
   * This function can only decide between normal documents and spiel elements.
   * You can not give it a synthetic document like a spiel.
   */
  private def getSpielType(doc: Document): SpielType =
    doc.additionalData.get("Referenz_auf_Spiel").exists(!_.isBlank) match
      case true  => SpielType.Element
      case false => SpielType.NormalDocument

