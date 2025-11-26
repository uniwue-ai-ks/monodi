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
import de.olyro.monodi.data.notes.CommentTree.CommentTreeUndecided
import de.olyro.monodi.data.notes.CommentTree.CommentTreeLeaf
import de.olyro.monodi.data.notes.CommentTree.CommentTreeGrid
import de.olyro.monodi.data.notes.CommentTreeLeafContent.Text
import de.olyro.monodi.data.notes.CommentTreeLeafContent.Bracket
import de.olyro.monodi.data.notes.CommentTreeLeafContent.Notes
import java.util.UUID

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
              case BookEntry.MetaData(lines)                                      => makeStage1Meta(accu, partialPage, todo, lines)
              case BookEntry.Document(id, content)                                => makeStage1Document(accu, partialPage, todo, id, content)
              case BookEntry.Cover(cover)                                         => makeStage1Cover(accu, partialPage, todo, cover)
              case BookEntry.TableOfContents(toc)                                 => makeStage1Toc(accu, partialPage, todo, toc)
              case BookEntry.SourceDescription(source)                            => makeStage1SourceDescription(accu, partialPage, todo, source)
              case BookEntry.CriticalApparatus(documentId, dokumentenId, content) =>
                makeStage1CriticalApparatus(accu, partialPage, todo, documentId, dokumentenId, content)

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
        .registerSourceDesc(source.id, pageNr),
      Nil,
      todo
    )

  private def makeStage1CriticalApparatus(
      accu: PrintStage1,
      partialPage: List[BoundingBox],
      todo: List[BookEntry],
      documentId: String,
      dokumentenId: String,
      content: RootContainer
  ): URIO[ProgressNotifier, PrintStage1] =
    val isFirstApparatus = !accu.tocInfo.keys.exists:
      case TocEntityId.CriticalApparatus(_) => true
      case _                                => false

    val (closed, partial) = isFirstApparatus match
      case false => (Vector.empty, partialPage)
      case true  => if partialPage.isEmpty then (Vector.empty, Nil) else (Vector(makePage(partialPage)), Nil)

    val sectionTitle =
      if isFirstApparatus then
        List(
          normalText("Kritischer Apparat", 1.5.ofSFS)
            .centerX(config.width / 2)
            .widenX(config.width)
            .widenY(normalText("X", 1.5.ofSFS).height * 1)
        )
      else Nil

    val boxes       = sectionTitle ++ renderCriticalApparatusBoxes(dokumentenId, content)
    val split       = splitDocumentOnPages(Vector.empty, partial, boxes, true, false)
    val pageNr      = accu.pageCount + 1 + closed.size + (if split.partFitOnFirstPage then 0 else 1)
    val tocEntityId = TocEntityId.CriticalApparatus(documentId)

    makeStage1(
      accu.addPages(closed.toList).addPages(split.closedPages).register(tocEntityId, pageNr),
      split.partial,
      todo
    )

  private def renderCriticalApparatusBoxes(dokumentenId: String, content: RootContainer): List[BoundingBox] =
    val titleBox = normalText(dokumentenId, 1.5.ofSFS)
      .widenX(config.width)
      .padTop(config.syllableFontSize)

    // Filter to only valid comments with trees
    val validComments = ViewModel
      .getCommentData(content)
      .flatMap: cd =>
        cd.comment match
          case Right(comment) => comment.tree.map(tree => (tree = tree, sig = cd.signatures, comment = comment))
          case Left(_)        => None

    if validComments.isEmpty then Nil
    else
      val commentTreeSvg = new comment_tree.CommentTreeSvg(svg)
      val padding        = config.syllableFontSize * 1.5

      // Draw all signature boxes
      val withSignatureBoxes = validComments.map: vc =>
        val sigBox = normalTextWithMarkers(vc.sig.mkString(""), 1.ofSFS)
        (tree = vc.tree, sig = sigBox, comment = vc.comment)

      // Find max signature width
      val maxSigWidth = withSignatureBoxes.map(_.sig.width).max

      // Draw trees and concatenate with aligned signatures
      val commentBoxes = withSignatureBoxes.map: firstPass =>
        val alignedSignatures = firstPass.sig.widenX(maxSigWidth)
        val availableWidth    = config.width - maxSigWidth - padding

        val renderedTree = commentTreeSvg.draw(firstPass.tree, Some(firstPass.comment), content)
        val maybeBroken  = if renderedTree.width > availableWidth then
          val brokenTree = breakAtRightBracket(firstPass.tree)
          commentTreeSvg.draw(brokenTree, Some(firstPass.comment), content)
        else renderedTree

        BoundingBox
          .concatXAlignByFunction(List(alignedSignatures, maybeBroken), padding, getCommentTreeBaseline)
          .widenX(config.width)

      titleBox :: commentBoxes

  private def getCommentTreeBaseline(bb: BoundingBox): Double =
    bb.resolve("line-marker-32")
      .map(_.y)
      .minOption
      .orElse(bb.resolve("baseline").map(_.y).minOption)
      .getOrElse(0.0)

  private def breakAtRightBracket(tree: CommentTree): CommentTree =
    def isBracketLeaf(ct: CommentTree): Boolean =
      ct match
        case CommentTreeLeaf(_, Bracket(), _) => true
        case _                                => false

    def tryBreakRow(row: List[CommentTree]): Option[(CommentTree, CommentTree)] =
      row.span(ct => !isBracketLeaf(ct)) match
        case (_, Nil)                         => None
        case (beforeBracket, bracketAndAfter) =>
          val firstRow  = CommentTreeGrid(
            id = UUID.randomUUID().toString,
            items = List(beforeBracket),
            justification = Some(Justification.Left)
          )
          val secondRow = CommentTreeGrid(
            id = UUID.randomUUID().toString,
            items = List(bracketAndAfter),
            justification = Some(Justification.Left)
          )
          Some((firstRow, secondRow))

    tree match
      case ctu: CommentTreeUndecided                 => ctu
      case ctl: CommentTreeLeaf                      => ctl
      case CommentTreeGrid(id, items, justification) =>
        val brokenChildren = items.map(_.map(breakAtRightBracket))
        brokenChildren match
          case singleRow :: Nil =>
            tryBreakRow(singleRow) match
              case None               => CommentTreeGrid(id, brokenChildren, justification)
              case Some((row1, row2)) =>
                CommentTreeGrid(
                  id,
                  List(row1 :: Nil, row2 :: Nil),
                  Some(Justification.Left)
                )
          case _                => CommentTreeGrid(id, brokenChildren, justification)

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
      val pageNumber    = item.entityId.flatMap(entityToPageNr.get)

      pageNumber match
        case None         =>
          leftText.padLeft(leftPadding)
        case Some(pageNr) =>
          val rightText   = normalText(pageNr.toString, size = 1.2.ofSFS)
          val middleSpace =
            Math.max(0, config.width - leftPadding - leftText.width - rightText.width - 2 * middlePadding)
          val middleText  = BoundingBox.repeatToWidth(normalText("."), middleSpace, 5)
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

  private def normalTextWithMarkers(text: String, size: Size = Size.sameAsSyllable): BoundingBox =
    TextBox.normal(text, config.useSystemFontNames, fontSize = Some(size.toDouble(svg)), setMetricMarkers = true)

  private def italicText(text: String, size: Size = Size.sameAsSyllable): BoundingBox =
    TextBox.italic(text, config.useSystemFontNames, fontSize = Some(size.toDouble(svg)))
