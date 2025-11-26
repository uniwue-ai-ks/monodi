package de.olyro.monodi
package exprt

import cats.data.State
import cats.implicits.*
import de.olyro.monodi.`exprt`.Svg.{LinePartSettings, svgDefs}
import de.olyro.monodi.data.notes.*
import scalatags.Text.all.*
import scalatags.Text.{svgAttrs as SA, svgTags as ST}
import scala.annotation.nowarn

final case class Svg(config: CanvasConfig):
  import ViewModel.Data

  def useSystemFontNames =
    copy(config = new CanvasConfig(config.embedFonts, useSystemFontNames = true, config.width, config.height))

  def draw(r: RootContainer): List[WithMeta[Line, String]] =
    ViewModel
      .getLineMetadata(r)
      .map(_.mapWithMeta(makeLine))
      .map(_.map(makeSVG(_, "")))

  def makeSVG(bb: BoundingBox, additionalStyles: String): String =
    s"""<?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <svg width="${bb.width.toString}px" height="${(bb.height + config.endOfDocumentPadding).toString}px" viewBox="0 0 ${bb.width.toString} ${(bb.height + config.endOfDocumentPadding).toString}" version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
        <defs>
          <style type="text/css">
        ${
        if config.useSystemFontNames then ""
        else
          s"""
            @font-face {
              font-family: 'TextNormal';
              ${
              if config.embedFonts then
                s"""src: url("data:font/truetype;charset=utf-8;base64${TextBox.fontNormalBase64}")"""
              else """src: url("ll.ttf")"""
            };
            }
            @font-face {
              font-family: 'TextNormal';
              font-style: italic;
              ${
              if config.embedFonts then
                s"""src: url("data:font/truetype;charset=utf-8;base64${TextBox.fontItalicBase64}")"""
              else """src: url("llit.ttf")"""
            };
            }
            @font-face {
              font-family: 'TextSmallCaps';
              ${
              if config.embedFonts then
                s"""src: url("data:font/truetype;charset=utf-8;base64${TextBox.fontSmallCapsBase64}")"""
              else """src: url("llsc.ttf")"""
            };
            }
          """
      }

            .clickable {
              cursor: pointer;
            }

            text[font-family=TextSmallCaps] {
              font-variant-ligatures: none;
            }

            ${additionalStyles}
          </style>
          ${svgDefs}
        </defs>
        ${BoundingBox.draw(bb).toString}
      </svg>
      """

  def drawPages(pages: List[RootContainer]): List[BoundingBox] =
    pages
      .traverse(rc => distinctSignatures(ViewModel.getLineMetadata(rc)))
      .runA(Nil)
      .value
      .map(ls => joinLines(drawLines(ls)))

  def drawAll(r: RootContainer): BoundingBox =
    joinLines(drawAllUnjoined(r))

  def drawAllUnjoined(r: RootContainer): List[BoundingBox] =
    drawLines(distinctSignatures(ViewModel.getLineMetadata(r)).runA(Nil).value)

  def joinLines(lines: List[BoundingBox]) = BoundingBox.concatY(lines, config.linePadding)

  def drawLines(lines: List[WithMeta[Line, Data]]) =
    final case class Accu(lineBoxesReversed: List[BoundingBox], lastHieararchy: Option[List[Int]])

    @scala.annotation.tailrec
    def hierarchyDifference(first: List[Int], second: List[Int]): Int = (first, second) match
      case (Nil, Nil)         => 0
      case (Nil, ss)          => ss.length
      case (fs, Nil)          => fs.length
      case (f :: fs, s :: ss) =>
        if f == s then hierarchyDifference(fs, ss)
        else 1 + Math.max(fs.length, ss.length)

    val linesWithPadding = lines.foldLeft(Accu(Nil, None))((accu, line) => {
      val topPadding = accu.lastHieararchy.fold(0.0)(lh => {
        Math.max(
          0,
          hierarchyDifference(lh, line.meta.hierarchy) - 1
        ) * config.additionalContainerLevelDifferencePadding
      })
      Accu(makeLine(line).padTop(topPadding) :: accu.lineBoxesReversed, Some(line.meta.hierarchy))
    })

    linesWithPadding.lineBoxesReversed.reverse

  private def makeLine(line: WithMeta[Line, Data]): BoundingBox =
    line.data match
      case Left(p)    => drawParatextLine(line.meta.signatures, p)
      case Right(lps) => drawLine(line.meta.signatures, lps.toList, line.meta.comments, line.meta.font)

  def distinctSignatures[D](lines: List[WithMeta[Line, D]]) = lines.traverse(line =>
    for
      memo <- State.get[List[String]]
      _    <- State.set(line.meta.signatures)
    yield if memo == line.meta.signatures then withoutSignatures(line) else line,
  )

  private def withoutSignatures[D](x: WithMeta[Line, D]) = x.copy(meta = x.meta.copy(signatures = Nil))

  def drawParatextLine(sigs: List[String], p: ParatextContainer, padToLineWidth: Boolean = true): BoundingBox =
    val pattern = """(?s)(.*)\(([fp].*)\)""".r

    val (text, folioChangeText) = p.text.trim match
      case pattern(text, folio) => (text, "|| " + folio)
      case _                    => (p.text, "")

    val drawn      =
      if p.comment.isEmpty then drawParatext(text)
      else drawCommentMarkers(drawParatext(text))((true, true, p.uuid), config.commentMarkerXOffsetRelative, 0.8)
    val line       = if padToLineWidth then drawn.widenX(lineWidth) else drawn
    val sig        = getSignature(line, sigs)
    val folioTextB = getFolioText(line, folioChangeText)

    BoundingBox.concatX(List(sig, line, folioTextB), 0, Some("line-container")).padTop(halfY(lineHalfs.last))

  def drawLine(sigs: List[String], lps: List[LinePart], cmts: List[Comment], font: FontKind): BoundingBox =
    val line        = drawLineParts(lps, font, cmts).widenX(lineWidth)
    val sig         = getSignature(line, sigs)
    val folioText   = getFolioText(lps)
    val folioTextB  = getFolioText(line, folioText)
    val assembled   = BoundingBox.concatX(List(sig, line, folioTextB), 0, Some("line-container"))
    val hiddenTexts = getHiddenTexts(lps)
    val fmtHidden   = TextBox.Formatting.hidden.copy(useSystemNames = config.useSystemFontNames)

    hiddenTexts.foldLeft(assembled)((bb, hidden) =>
      bb.addAtId(TextBox.raw(hidden, None, fmtHidden, false).hideFromLayout, "syllable-text", Point(0, 0), true),
    )

  def getFolioText(lps: List[LinePart]): String =
    Some(lps.collect({ case fc: FolioChange => fc }).map(_.text).mkString)
      .filter(_.nonEmpty)
      .fold("")(s => "|| " + normalizeFolioText(s))

  // different editors write down folio changes differently. Some are putting a "f. " before it, others dont
  private val fNeededBeforeFolioText           = "^[0-9]+v?$".r
  def normalizeFolioText(text: String): String =
    if fNeededBeforeFolioText.matches(text.trim) then "f. " + text
    else text

  def getHiddenTexts(l: List[LinePart]): List[String] =
    val ns              = Container.getAllNotes(ZeileContainer("", l))
    val normalText      = l.flatMap(ViewModel.getLinePartText).mkString(" ")
    val normalizedText  = normalText.replaceAll("-\\s*", "")
    val normalizedText2 = normalText.replaceAll("-\\s*", "-")
    List(
      normalText,
      normalizedText,
      normalizedText2,
      NoteStringifier.directions(ns),
      NoteStringifier.intervals(ns)
    ).map(_.trim()).filter(_.nonEmpty)

  def getSignature(line: BoundingBox, sigs: List[String]): BoundingBox =
    TextBox
      .normal(sigs.mkString(""), config.useSystemFontNames, fontSize = Some(config.syllableFontSize))
      .widenX(config.signatureWidth)
      .centerY(getMiddleStaff(line, Some("syllable-text")))

  def getFolioText(line: BoundingBox, text: String): BoundingBox =
    TextBox
      .normal(text, config.useSystemFontNames, fontSize = Some(config.syllableFontSize))
      .widenX(config.folioTextWidth)
      .centerY(getMiddleStaff(line, Some("folio-change")))
      .padLeft(config.folioTextWidth / 2)

  def addSignature(line: BoundingBox, sigs: List[String]): BoundingBox =
    BoundingBox.concatX(
      List(
        TextBox
          .normal(sigs.mkString(""), config.useSystemFontNames)
          .widenX(40)
          .centerY(getMiddleStaff(line, Some("syllable-text"))),
        line
      ),
      0,
      Some("signature-container")
    )

  def getMiddleStaff(line: BoundingBox, reference: Option[String]): Double =
    val referencePoint = reference.flatMap(id => line.resolve(id).headOption)

    line
      .resolve("line-marker-34")
      .headOption
      .map(_.y) match
      case Some(middle) => middle
      case None         => referencePoint.fold(line.height / 2)(_.y)

  def drawParatext(text: String): BoundingBox =
    def wrap(text: String) = BoundingBox.wrapX(
      text.toUpperCase
        .split("\\s+")
        .toList
        .map(t =>
          TextBox.normal(t, config.useSystemFontNames, Some("paratext"), fontSize = Some(config.syllableFontSize))
        ),
      xPadding = config.paratextWordPadding,
      xMax = lineWidth,
      xIndent = 0
    )

    BoundingBox.concatY(
      text.linesIterator.flatMap(wrap).toList,
      config.paratextLinePadding,
      Some("paratext-text-block")
    )

  def wrapText(
      text: String,
      makeBb: String => BoundingBox,
      lineWidth: Double,
      wordPadding: Double,
      linePadding: Double,
      id: Option[String] = None
  ): BoundingBox =
    def wrap(text: String) = BoundingBox.wrapX(
      text.split("\\s+").toList.map(makeBb),
      xPadding = wordPadding,
      xMax = lineWidth,
      xIndent = 0
    )

    BoundingBox.concatY(
      text.linesIterator.flatMap(wrap).toList,
      linePadding,
      id
    )

  def drawLineParts(lineParts: List[LinePart], font: FontKind, comments: List[Comment]): BoundingBox =
    import config.{lineContinuationIndent, linePadding, linePartPadding}

    val minNoteHalfs                                =
      ((lineHalfs.head - 2) :: Container.getAllNotes(ZeileContainer("", lineParts)).map(_.halfs - 2)).min
    def mkLPS(pl: Double, pr: Double, lp: LinePart) =
      LinePartSettings(font, minNoteHalfs, pl, pr, ViewModel.getCommentInfo(lp, comments))

    // scala linter incorrectly reports `a +: b :+ c` pattern to fail on `List(_, _)`
    @nowarn("msg=match may not be exhaustive.")
    val settings = (lineParts match {
      case Nil                  => Nil
      case lp :: Nil            => List(mkLPS(0.0, 0.0, lp))
      case first +: mid :+ last =>
        mkLPS(0.0, linePartPadding / 2.0, first) +: mid.map(
          mkLPS(linePartPadding / 2.0, linePartPadding / 2.0, _)
        ) :+ mkLPS(linePartPadding / 2.0, 0.0, last)
    })

    val parts = (lineParts zip settings) flatMap { case (lp, settings) => drawLinePart(settings)(lp) }

    val lines                        = BoundingBox.wrapX(parts, xPadding = 0, xMax = lineWidth, xIndent = lineContinuationIndent)
    val withCriticalApparatusMarkers =
      lines.map(line =>
        Svg.findBoundingBoxForBorderInCriticalApparatus(line) match {
          case None       => line
          case Some(rect) =>
            val upperDummyPoint = Point(rect.upperLeft.x, halfY(lineHalfs.last + 2))
            val lowerDummyPoint = Point(rect.upperLeft.x, halfY(lineHalfs.head - 1))
            val dummyRect       = Rect.create(lowerDummyPoint, upperDummyPoint)
            val padded          = rect.combine(dummyRect).pad(5)
            line.addAt(
              BoundingBox(
                width = padded.width,
                height = padded.height,
                subs = List(
                  Sub(
                    start = Point(1, 1),
                    sub = Right(p =>
                      ST.rect(
                        SA.x           := p.x,
                        SA.y           := p.y,
                        SA.width       := (padded.width - 2).max(0).toString,
                        SA.height      := (padded.height - 2).max(0).toString,
                        SA.stroke      := "black",
                        SA.fill        := "none",
                        SA.strokeWidth := 1
                      ),
                    )
                  )
                ),
                full = true
              ),
              padded.upperLeft
            )
        },
      )
    BoundingBox.concatY(withCriticalApparatusMarkers, linePadding, Some("line-block"))

  def drawCommentMarkers(inner: BoundingBox)(
      lri: (Boolean, Boolean, String),
      xOffset: Double = config.commentMarkerXOffsetRelative,
      yOffset: Double = config.commentMarkerYOffsetRelative
  ) =
    val onClickMessage = s"monodi-open-popup:${lri._3}"

    if config.drawCommentMarks then
      val dxl = config.commentL(onClickMessage).width * (xOffset - 1)
      val dxr = config.commentR(onClickMessage).width * -xOffset
      val dyl = config.commentL(onClickMessage).height * -yOffset
      val dyr = config.commentR(onClickMessage).height * -yOffset
      BoundingBox(
        inner.width,
        inner.height,
        List(
          if lri._1 then Some(Sub(Point(dxl, inner.height + dyl), Left(config.commentL(onClickMessage)))) else None,
          Some(Sub(Point(0, 0), Left(inner))),
          if lri._2 then Some(Sub(Point(inner.width + dxr, inner.height + dyr), Left(config.commentR(onClickMessage))))
          else None
        ).flatten
      )
    else inner

  def drawLinePart(settings: LinePartSettings)(lp: LinePart): Option[BoundingBox] =
    lp match
      case s: Syllable     => Some(drawSyllable(settings)(s))
      case fc: FolioChange => Some(folioChange(settings, fc.hasNotes))
      case lc: LineChange  => Some(lineChange(settings, lc.hasNotes))
      case _: Clef         => None
      case _: Box          => Some(BoundingBox(0, 0, Nil, Some("box-line-part")))

  private def vBar(x: Double, y: Double, scale: Double) = Sub(
    Point(x, y),
    Right(p =>
      ST.line(
        SA.x1          := p.x,
        SA.x2          := p.x,
        SA.y1          := p.y,
        SA.y2          := p.y + 20 * scale,
        SA.strokeWidth := 1 * scale,
        SA.stroke      := "black"
      ),
    )
  )

  def folioChange(s: LinePartSettings, drawStaffLines: Boolean) =
    val box = drawCommentMarkers(
      BoundingBox(
        10 * config.relativeSyllableFontScale,
        (halfY(s.minNoteHalfs) + config.linePadding) * config.relativeSyllableFontScale,
        List(
          Sub(
            Point(3, halfY(s.minNoteHalfs)),
            Left(
              BoundingBox(
                0,
                0,
                List(
                  vBar(0, 0, config.relativeSyllableFontScale),
                  vBar(4 * config.relativeSyllableFontScale, 0, config.relativeSyllableFontScale)
                ),
                Some("folio-change")
              )
            )
          )
        )
      )
    )(s.commentMarks)
      .padLeft(s.leftPadding)
      .padRight(s.rightPadding)
    if drawStaffLines then addStaffLines(box) else box

  def lineChange(s: LinePartSettings, drawStaffLines: Boolean) =
    val box = drawCommentMarkers(
      BoundingBox(
        10 * config.relativeSyllableFontScale,
        (halfY(s.minNoteHalfs) + config.linePadding) * config.relativeSyllableFontScale,
        List(
          Sub(
            Point(5, halfY(s.minNoteHalfs)),
            Left(BoundingBox(0, 0, List(vBar(0, 0, config.relativeSyllableFontScale)), Some("line-change")))
          )
        )
      )
    )(s.commentMarks)
      .padLeft(s.leftPadding)
      .padRight(s.rightPadding)
    if drawStaffLines then addStaffLines(box) else box

  def drawSyllable(s: LinePartSettings)(syl: Syllable): BoundingBox =
    val notes   =
      if syl.syllableType != SyllableType.Normal then
        BoundingBox(0, halfY(s.minNoteHalfs), Nil, Some("without-notes-padding"))
      else drawNotes(syl.notes, s.minNoteHalfs)
    val rawText = removeEditorPlaceholders(syl.text)
    val text    = s.font match
      case FontKind.Italic =>
        TextBox.italic(
          rawText,
          config.useSystemFontNames,
          Some("syllable-text"),
          fontSize = Some(config.syllableFontSize)
        )
      case FontKind.Caps   =>
        TextBox.smallCaps(
          rawText,
          config.useSystemFontNames,
          Some("syllable-text"),
          fontSize = Some(config.syllableFontSize)
        )
      case FontKind.Normal =>
        TextBox.normal(
          rawText,
          config.useSystemFontNames,
          Some("syllable-text"),
          fontSize = Some(config.syllableFontSize)
        )

    val padding = config.linePartPadding / 2.0
    val box     =
      drawCommentMarkers(BoundingBox.concatY(List(notes, text), 0, Some("syllable")))(
        s.commentMarks,
        0.3,
        config.commentMarkerYOffsetRelative
      )
        .padLeft(padding + s.leftPadding)
        .padRight(padding + s.rightPadding)

    if syl.syllableType != SyllableType.Normal then box else addStaffLines(box)

  def removeEditorPlaceholders(s: String): String = s.replace("jjj", "").replace("JJJ", "").replace("Jjj", "")

  def addStaffLines(box: BoundingBox): BoundingBox =
    lineHalfs.foldLeft(box)(addStaffLine(0, 0))

  def addStaffLine(jutLeft: Double, jutRight: Double)(box: BoundingBox, halfs: Int): BoundingBox =
    box.copy(subs =
      Sub(
        Point(0, halfY(halfs)),
        Left(
          BoundingBox(
            box.width,
            1,
            List(
              Sub(
                Point(0, 0),
                Right((start: Point) =>
                  ST.line(
                    SA.x1          := start.x - jutLeft,
                    SA.x2          := start.x + box.width + jutRight,
                    SA.y1          := start.y,
                    SA.y2          := start.y,
                    SA.strokeWidth := 1,
                    SA.stroke      := "black"
                  )(),
                )
              )
            ),
            Some(s"line-marker-$halfs"),
            full = true
          )
        )
      ) :: box.subs,
    )

  def drawNotes(s: Spaced, minNoteHalfs: Int): BoundingBox =
    final case class NoteLike(bb: BoundingBox, start: Note, end: Note)

    def getPath(y: Double, width: Double): Sub =
      val r      = (config.note.height / 2).toString
      val height = config.note.height

      Sub(
        Point(0, y - height),
        Left(
          BoundingBox(
            width,
            height,
            List(
              Sub(
                Point(0, height),
                Right(p => raw(s"""
          <path stroke-linecap="round" stroke="black" stroke-width="2" fill="none" d="
            M ${p.x.toString} ${p.y.toString}
            l 0 -$r
            a $r $r 0 0 1 $r -$r
            l ${(width - 10).toString} 0
            a $r $r 0 0 1 $r $r
            l 0 $r
            " />
          """))
              )
            ),
            Some("tie"),
            full = true
          )
        )
      )

    def makeGroup(grouped: Grouped): Option[NoteLike] =
      grouped.grouped match
        case Nil => None
        case all => {
          val bb      = BoundingBox.concatXF(all)(drawNote(_, minNoteHalfs), config.groupNoteSpace, Some("group"))
          val maxHalf = all.map(n => n.base.halfs(n.octave)).max
          val tieY    = halfY(maxHalf + (if maxHalf % 2 == 0 then 3 else 4))
          Some(
            if all.length > 1 then NoteLike(bb.copy(subs = getPath(tieY, bb.width) :: bb.subs), all.head, all.last)
            else NoteLike(bb, all.head, all.last)
          )
        }

    def makeNonSpaced(nonSpaced: NonSpaced): Option[NoteLike] =
      nonSpaced.nonSpaced.flatMap(makeGroup) match
        case Nil => None
        case all =>
          Some(
            NoteLike(
              BoundingBox.concatXF(all)(_.bb, (a, b) => config.nonSpacedNoteSpace(a.end, b.start), Some("nonSpaced")),
              all.head.start,
              all.last.end
            )
          )

    val bbs = s.spaced.flatMap(makeNonSpaced)
    BoundingBox
      .concatXF(bbs)(_.bb, (a, b) => config.spacedNoteSpace(a.end, b.start), Some("spaced"))
      .widenY(halfY(minNoteHalfs))

  def drawNote(n: Note, minNoteHalfs: Int): BoundingBox =
    val noteHalf = n.base.halfs(n.octave)
    val lineY    = halfY(noteHalf)
    val maxY     = halfY(minNoteHalfs)

    val bb = n.noteType match
      case NoteType.Normal     => config.note
      case NoteType.Oriscus    => config.oriscus
      case NoteType.Flat       => config.flat
      case NoteType.Sharp      => config.sharp
      case NoteType.Strophicus => config.strophicus
      case NoteType.Natural    => config.natural
      case NoteType.Quilisma   => config.quilisma
      case _                   => config.note

    val centerCorrection = n.noteType match
      case NoteType.Flat       => -2.0
      case NoteType.Strophicus => 2.0
      case _                   => 0.0

    val posY = lineY + centerCorrection
    val note =
      if n.liquescent then bb.scaleWith(config.liquescentScale).centerY(posY).widenY(maxY)
      else bb.centerY(posY).widenY(maxY)

    val neededExtraLines = config.minNote
      .to(config.maxNote)
      .filter(halfs =>
        (halfs % 2 == 0) && (halfs < lineHalfs.head && halfs >= noteHalf || halfs > lineHalfs.last && halfs <= noteHalf),
      )

    neededExtraLines.foldLeft(note)(addStaffLine(config.noteLineJutLeft, config.noteLineJutRight))

  def noteY(octave: Int, base: BaseNote): Double =
    halfY(base.halfs(octave))

  def halfY(halfs: Int): Double = (config.maxNote - halfs) * config.note.height / 2.0

  val lineHalfs: List[Int] = List(
    (4, BaseNote.E),
    (4, BaseNote.G),
    (4, BaseNote.B),
    (5, BaseNote.D),
    (5, BaseNote.F)
  ).map(t => t._2.halfs(t._1))

  // the folio text is currently left-padded with half its length, which is why
  // we need to multiply it by 1.5
  def lineWidth = config.width - config.signatureWidth - config.folioTextWidth * 1.5

object Svg:
  val defaultSvg = Svg(CanvasConfig.defaultConfig)
  val svgDefs    = Util.unsafeReadResource(Svg.getClass, "svgdefs.svg")

  case class LinePartSettings(
      font: FontKind,
      minNoteHalfs: Int,
      leftPadding: Double,
      rightPadding: Double,
      commentMarks: (Boolean, Boolean, String)
  )

  private def findBoundingBoxForBorderInCriticalApparatus(line: BoundingBox): Option[Rect] =
    sealed trait Result derives CanEqual
    case object NoResultYet                                  extends Result
    final case class ContinueCollecting(found: Vector[Rect]) extends Result
    final case class StopCollecting(found: Vector[Rect])     extends Result

    def isBox(bb: BoundingBox): Boolean =
      bb.id.exists(_.contains("box-line-part"))

    def needsToBeBordered(bb: BoundingBox): Boolean =
      bb.id.exists(id => id.startsWith("note") || id == "tie")

    def go(offset: Point, collecting: Boolean, bb: BoundingBox): Result =
      if collecting then
        if isBox(bb) then StopCollecting(Vector.empty)
        else if bb.full then
          if needsToBeBordered(bb) then
            ContinueCollecting(Vector(Rect.create(offset, offset + Point(bb.width, bb.height))))
          else ContinueCollecting(Vector.empty)
        else
          bb.subs.foldLeft(ContinueCollecting(Vector.empty): Result)((acc, sub) =>
            acc match {
              case _: StopCollecting         => acc
              case NoResultYet               => throw new RuntimeException("Should not happen to get NRY when collecting")
              case ContinueCollecting(found) =>
                sub.sub match {
                  case Right(_)    => acc
                  case Left(subBb) =>
                    go(offset + sub.start, true, subBb) match {
                      case StopCollecting(newlyFound)     => StopCollecting(found ++ newlyFound)
                      case ContinueCollecting(newlyFound) => ContinueCollecting(found ++ newlyFound)
                      case NoResultYet                    => throw new RuntimeException("Can't get NRY when collecting")
                    }
                }
            },
          )
      else if isBox(bb) then ContinueCollecting(Vector.empty)
      else if bb.full then NoResultYet
      else
        bb.subs.foldLeft(NoResultYet: Result)((acc, sub) =>
          sub.sub match {
            case Right(_)    => acc
            case Left(subBB) =>
              acc match {
                case _: StopCollecting         => acc
                case NoResultYet               =>
                  go(offset + sub.start, false, subBB) match {
                    case StopCollecting(found)     => StopCollecting(found)
                    case ContinueCollecting(found) => ContinueCollecting(found)
                    case NoResultYet               => NoResultYet
                  }
                case ContinueCollecting(found) =>
                  go(offset + sub.start, true, subBB) match {
                    case StopCollecting(newlyFound)     => StopCollecting(found ++ newlyFound)
                    case ContinueCollecting(newlyFound) => ContinueCollecting(found ++ newlyFound)
                    case NoResultYet                    => throw new RuntimeException("NRY when collecting not possible")
                  }
              }
          },
        )

    go(Point(0, 0), false, line) match
      case NoResultYet | ContinueCollecting(_) => None
      case StopCollecting(found)               =>
        if found.nonEmpty then Some(found.tail.foldLeft(found.head)((acc, rect) => acc.combine(rect)))
        else None
