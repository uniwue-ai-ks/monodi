package de.olyro.monodi.exprt
package print

import zio.*
import de.olyro.monodi.data.notes.*
import cover as C
import hierarchy as H
import de.olyro.monodi.exprt.print.Size.*
import de.olyro.monodi.exprt.print.markdown.Markdown
import de.olyro.monodi.data.Source
import H.TocEntityId

final case class Print(
    svg: Svg,
    settings: Settings
):
  val config        = svg.config
  val contentHeight = config.height - (if settings.pageNumberPrefix.nonEmpty then config.syllableFontSize * 3 else 0)
  val contentStartY = config.height - contentHeight

  def drawPrintView(rc: RootContainer): UIO[List[BoundingBox]] =
    drawPrintView(List(BookEntry.Document("", rc))).map(_.map(_.content)).provide(ProgressNotifier.dummy)

  def drawPrintView(entries: List[BookEntry]): URIO[ProgressNotifier, List[Page]] =
    for
      stage1          <- makeStage1(PrintStage1.empty, Nil, entries)
      pageNumberOffset = stage1.items.takeWhile(!_.hasPageNumbers).map(_.pageCount).sum
      pages           <- makeStage2(stage1)
    yield maybeAddPageNumbers(pageNumberOffset, pages.toVector).toList

  private def makeStage1(
      accu: PrintStage1,
      partialPage: List[BoundingBox],
      todo: List[BookEntry]
  ): URIO[ProgressNotifier, PrintStage1] =
    ProgressNotifier
      .notify(Progress.Layouting(accu.pageCount + 1, 1))
      .`*>`:
        todo match
          case Nil =>
            ZIO.succeed:
              if partialPage.isEmpty then accu
              else accu.addPage(makePage(partialPage))

          case bookEntry :: todo =>
            bookEntry match
              case BookEntry.MetaData(lines)                        => makeStage1Meta(accu, partialPage, todo, lines)
              case BookEntry.Document(id, content)                  => makeStage1Document(accu, partialPage, todo, id, content)
              case BookEntry.Cover(cover)                           => makeStage1Cover(accu, partialPage, todo, cover)
              case BookEntry.TableOfContents(toc)                   => makeStage1Toc(accu, partialPage, todo, toc)
              case BookEntry.SourceDescription(source)              => makeStage1SourceDescription(accu, partialPage, todo, source)
              case BookEntry.CriticalApparatus(documentId, content) =>
                makeStage1CriticalApparatus(accu, partialPage, todo, documentId, content)

  private def makeStage1Meta(
      accu: PrintStage1,
      partialPage: List[BoundingBox],
      todo: List[BookEntry],
      lines: List[LineItem]
  ): URIO[ProgressNotifier, PrintStage1] =
    val rendered = BoundingBox
      .concatY(
        lines.map({
          case LineItem.VSkip(size)                        => BoundingBox.empty.widenY(size.toDouble(svg))
          case LineItem.Text(text, size, _, justification) =>
            val box = normalText(text, size)
            justification match
              case None                => box
              case Some(justification) =>
                import de.olyro.monodi.data.notes.Justification.*
                justification match
                  case Left   => box
                  case Center => box.centerX(config.width / 2)
                  case Right  => box.padLeft(config.width - box.width)

        }),
        0
      )
      .widenX(config.width)

    if partialPage.isEmpty || fitsOnePage(partialPage :+ rendered) then makeStage1(accu, partialPage :+ rendered, todo)
    else makeStage1(accu.addPage(makePage(partialPage)), List(rendered), todo)

  private def makeStage1Document(
      accu: PrintStage1,
      partialPage: List[BoundingBox],
      todo: List[BookEntry],
      id: String,
      content: RootContainer
  ): URIO[ProgressNotifier, PrintStage1] =
    val lines  = svg.drawAllUnjoined(content)
    val split  = splitDocumentOnPages(Vector.empty, partialPage, lines, true, false)
    val pageNr = if split.partFitOnFirstPage then accu.pageCount + 1 else accu.pageCount + 2
    ensureTopMargin(accu.addPages(split.closedPages).registerDoc(id, pageNr), split.partial, todo, 0.05.ofPH)

  /**
   * Makes sure that the next data will either have a top margin of size
   * or is at the start of the page.
   */
  private def ensureTopMargin(
      accu: PrintStage1,
      partialPage: List[BoundingBox],
      todo: List[BookEntry],
      size: Size
  ): URIO[ProgressNotifier, PrintStage1] =
    if partialPage.isEmpty then makeStage1(accu, partialPage, todo)
    else
      val vSize        = size.toDouble(svg)
      val pageSizeUsed = makePage(partialPage).height
      val pageSizeLeft = config.height - pageSizeUsed
      if pageSizeLeft < vSize then makeStage1(accu.addPage(makePage(partialPage)), Nil, todo)
      else makeStage1(accu, partialPage :+ BoundingBox.empty.widenY(vSize).widenX(config.width), todo)

  final private case class DocumentSplitResult(
      closedPages: Vector[BoundingBox],
      partial: List[BoundingBox],
      partFitOnFirstPage: Boolean
  )

  private def splitDocumentOnPages(
      closedPages: Vector[BoundingBox],
      partial: List[BoundingBox],
      linesToDo: List[BoundingBox],
      isFirstPage: Boolean,
      didFitOnFirstPage: Boolean
  ): DocumentSplitResult =
    linesToDo match
      case Nil               => DocumentSplitResult(closedPages, partial, didFitOnFirstPage)
      case line :: linesToDo =>
        if fitsOnePage(partial :+ line) || partial.isEmpty then
          val newDidFitOnFirstPage = didFitOnFirstPage || isFirstPage
          splitDocumentOnPages(closedPages, partial :+ line, linesToDo, false, newDidFitOnFirstPage)
        else splitDocumentOnPages(closedPages :+ makePage(partial), List(line), linesToDo, false, didFitOnFirstPage)

  private def makeStage1Cover(
      accu: PrintStage1,
      partialPage: List[BoundingBox],
      todo: List[BookEntry],
      cover: C.Cover
  ): URIO[ProgressNotifier, PrintStage1] =
    val closed = if partialPage.isEmpty then None else Some(makePage(partialPage))
    makeStage1(accu.addPages(closed.toList).addCover(cover), Nil, todo)

  private def makeStage1SourceDescription(
      accu: PrintStage1,
      partialPage: List[BoundingBox],
      todo: List[BookEntry],
      source: Source
  ): URIO[ProgressNotifier, PrintStage1] =
    val closed = if partialPage.isEmpty then None else Some(makePage(partialPage))
    val pageNr = accu.pageCount + 1 + closed.toList.size
    makeStage1(
      accu
        .addPages(closed.toList)
        .addPages(Markdown.render(svg, source.beschreibung, contentStartY))
        .registerSourceDesc(source.id, pageNr)
        ,
      Nil,
      todo
    )

  private def makeStage1CriticalApparatus(
      accu: PrintStage1,
      partialPage: List[BoundingBox],
      todo: List[BookEntry],
      documentId: String,
      content: RootContainer
  ): URIO[ProgressNotifier, PrintStage1] =
    val isFirstApparatus = !accu.tocInfo.keys.exists:
      case TocEntityId.CriticalApparatus(_) => true
      case _                                => false

    val tocEntityId = TocEntityId.CriticalApparatus(documentId)
    val pageNr      =
      if isFirstApparatus then accu.pageCount + 1
      else accu.pageCount + 2

    if isFirstApparatus then
      val closed = if partialPage.isEmpty then None else Some(makePage(partialPage))
      val pages  = renderCriticalApparatus(documentId, content)
      makeStage1(accu.addPages(closed.toList).addPages(pages).register(tocEntityId, pageNr), Nil, todo)
    else
      val boxes = renderCriticalApparatusBoxes(documentId, content)
      val split = splitDocumentOnPages(Vector.empty, partialPage, boxes, true, false)
      makeStage1(accu.addPages(split.closedPages).register(tocEntityId, pageNr), split.partial, todo)

  private def renderCriticalApparatus(documentId: String, content: RootContainer): Vector[BoundingBox] =
    if content.comments.isEmpty then Vector.empty
    else
      val boxes = renderCriticalApparatusBoxes(documentId, content)
      val split = splitDocumentOnPages(Vector.empty, Nil, boxes, true, false)
      split.closedPages ++ Some(split.partial).filter(_.nonEmpty).map(makePage)

  private def renderCriticalApparatusBoxes(documentId: String, content: RootContainer): List[BoundingBox] =
    if content.comments.isEmpty then Nil
    else
      val title    = if documentId.isEmpty then "Critical Apparatus" else s"Critical Apparatus: $documentId"
      val titleBox = normalText(title, 1.5.ofSFS)
        .centerX(config.width / 2)
        .widenX(config.width)
        .widenY(normalText("X", 1.5.ofSFS).height * 2)

      val commentTreeSvg = new comment_tree.CommentTreeSvg(svg)
      val commentBoxes   = content.comments.flatMap { comment =>
        comment.tree.map { tree =>
          val renderedTree = commentTreeSvg.draw(tree, Some(comment), content)
          renderedTree.widenX(config.width)
        }
      }

      titleBox :: commentBoxes

  private def makeStage1Toc(
      accu: PrintStage1,
      partialPage: List[BoundingBox],
      todo: List[BookEntry],
      toc: List[H.TableOfContentsViewItem]
  ): URIO[ProgressNotifier, PrintStage1] =
    val closed = if partialPage.isEmpty then None else Some(makePage(partialPage))
    val pages  = renderTocItems(toc, Map.empty)
    makeStage1(accu.addPages(closed.toList).addToc(toc, pages.size), Nil, todo)

  private def renderTocItems(
      toc: List[H.TableOfContentsViewItem],
      entityToPageNr: Map[TocEntityId, Int]
  ): Vector[BoundingBox] =
    val lines = toc.map(item =>
      val leftPadding   = item.depth * 10
      val middlePadding = 10
      val leftText      = normalText(item.text, size = 1.2.ofSFS)
      val rightText     = normalText(item.entityId.flatMap(entityToPageNr.get).fold("?")(_.toString), size = 1.2.ofSFS)
      val middleSpace   = Math.max(0, config.width - leftPadding - leftText.width - rightText.width - 2 * middlePadding)
      val middleText    = BoundingBox.repeatToWidth(normalText("."), middleSpace, 5)
      BoundingBox
        .concatX(
          List(leftText, middleText, rightText),
          middlePadding
        )
        .padLeft(leftPadding)
    )
    val split = splitDocumentOnPages(Vector.empty, Nil, lines, true, false)
    split.closedPages ++ Some(split.partial).filter(_.nonEmpty).map(makePage)

  private def makeStage2(stage1: PrintStage1): URIO[ProgressNotifier, List[Page]] =
    ZIO
      .foreach(stage1.items.zipWithIndex): (item, idx) =>
        ProgressNotifier
          .notify(Progress.Layouting(idx + 1, 2))
          .as:
            item match
              case PrintStage1.Item.Page(box) => List(Page(box, None))

              case PrintStage1.Item.Cover(cover) =>
                val (box, css) = C.CoverRenderer.render(cover, svg)
                List(Page(box, Some(css)))

              case PrintStage1.Item.TableOfContents(toc, _) =>
                renderTocItems(toc, stage1.tocInfo).map(Page(_, None))
      .map(_.flatten.toList)

  private def makePage(boxes: List[BoundingBox]): BoundingBox =
    svg.joinLines(boxes).padTop(contentStartY)

  private def maybeAddPageNumbers(offset: Int, pages: Vector[Page]): Vector[Page] =
    settings.pageNumberPrefix match
      case None         => pages
      case Some(prefix) =>
        pages.zipWithIndex.map((p, i) =>
          if i < offset then p
          else p.copy(content = addPageNumber(p.content, prefix, i + 1))
        )

  private def addPageNumber(bb: BoundingBox, prefix: String, pageNumber: Int): BoundingBox =
    val toAdd    = normalText(s"$prefix $pageNumber", 1.ofSFS)
    val padding  = (bb.width - toAdd.width) / 2
    val finalBox = BoundingBox.underlineBox(
      toAdd
        .padLeft(padding)
        .padRight(padding),
      0
    )
    bb.addAt(finalBox, Point(0, 0))

  private def fitsOnePage(boxes: List[BoundingBox]): Boolean =
    svg.joinLines(boxes).height <= contentHeight

  private def normalText(text: String, size: Size = Size.sameAsSyllable): BoundingBox =
    TextBox.normal(text, config.useSystemFontNames, fontSize = Some(size.toDouble(svg)))

  private def italicText(text: String, size: Size = Size.sameAsSyllable): BoundingBox =
    TextBox.italic(text, config.useSystemFontNames, fontSize = Some(size.toDouble(svg)))
