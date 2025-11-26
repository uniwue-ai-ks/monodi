package de.olyro.monodi.exprt.renderer

import de.olyro.monodi.`exprt`.FontKind
import de.olyro.monodi.`exprt`.renderer.Typesetting.Placement
import de.olyro.monodi.data.notes.{Box as _, *}
import de.olyro.monodi.Util.scalatagsImplicits.*
import scalatags.Text.{svgAttrs as ^, svgTags as <}
import scalatags.text.Frag

import scala.Option.when
import scala.annotation.tailrec

object Svg:

  object Templates:
    val note       = "note-template"
    val oriscus    = "oriscus-template"
    val flat       = "flat-template"
    val sharp      = "sharp-template"
    val strophicus = "strophicus-template"
    val natural    = "natural-template"
    val quilisma   = "quilisma-template"
    val commentL   = "corner-bottom-left"
    val commentR   = "corner-bottom-right"

    def fromNoteType(nt: NoteType) = nt match
      case NoteType.Normal     => Templates.note
      case NoteType.Liquescent => Templates.note
      case NoteType.Oriscus    => Templates.oriscus
      case NoteType.Quilisma   => Templates.quilisma
      case NoteType.Strophicus => Templates.strophicus
      case NoteType.Flat       => Templates.flat
      case NoteType.Sharp      => Templates.sharp
      case NoteType.Natural    => Templates.natural
      case _                   => Templates.note

  // private lazy val svgDefs = Util.unsafeReadResource(Svg.getClass, "svgdefs.svg")
  private val svgDefs   = de.olyro.monodi.`exprt`.Svg.svgDefs
  private val xmlHeader = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>"""

  def mkSvg(width: Double, height: Double, content: List[Frag]): Frag = <.svg(
    ^.width               := s"${width}px",
    ^.height              := s"${height}px",
    ^.viewBox             := s"0 0 $width $height",
    ^.attr("version")     := "1.1",
    ^.attr("xmlns")       := "http://www.w3.org/2000/svg",
    ^.attr("xmlns:xlink") := "http://www.w3.org/1999/xlink",
    <.defs(
      <.tag("style")(
        ^.`type` := "text/css",
        "@font-face { font-family: 'TextNormal'; src: url('tnr.ttf'); }",
        "@font-face { font-family: 'TextSmallCaps'; src: url('cr.ttf'); }",
      ),
      raw(svgDefs),
    ),
    content,
  )

  def draw(l: SynopsisLine): String =
    import Config.*

    val ltp = lineTopPadding
    val lpp = linePartPadding
    val eod = endOfDocumentPadding

    def drawHeader(parts: List[Box]): List[Frag] = parts
      .foldLeft((List.empty[Frag], signatureWidth)) { case ((acc, x), box) =>
        (
          acc ++ drawBox(x, lineTopPadding, box),
          x + box.width + 2 * lpp,
        )
      }
      ._1

    val hWidth   = l.header.map(_.width).sum + (l.header.size - 1) * 2 * lpp
    val hHeight  = l.header.map(_.height).maxOption getOrElse 0.0
    val hYAnchor = l.header.headOption.map(_.yAnchor) getOrElse 0.0
    val nb       = prepareNotesLine(NotesLine(l.signature, l.folioLabel, l.segments))

    val hasHeaders = l.header.nonEmpty || l.segments.exists(_.header.isDefined)

    if !hasHeaders then
      xmlHeader + mkSvg(nb.width, ltp - nb.yAnchor + nb.height + eod, drawNotesLine(0, ltp, nb).value).render
    else if l.segments.isEmpty then
      xmlHeader + mkSvg(signatureWidth + hWidth, ltp - hYAnchor + hHeight + eod, drawHeader(l.header)).render
    else
      val notesY = 2 * ltp - hYAnchor + hHeight
      val height = 2 * ltp - hYAnchor + hHeight - nb.yAnchor + nb.height + eod
      xmlHeader + mkSvg(
        signatureWidth + (hWidth max nb.width),
        height,
        drawHeader(l.header) ++ drawNotesLine(0, notesY, nb).value,
      ).render

  final case class NotesLineProtoBox(width: Double, height: Double, yAnchor: Double, spacing: Double, line: NotesLine)

  def prepareNotesLine(l: NotesLine): NotesLineProtoBox =
    import Config.*

    val width      = signatureWidth + folioTextWidth / 2 + l.folioLabel.map(_.box.width).getOrElse(0.0) +
      (if l.segments.isEmpty then 0 else l.segments.map(_.width).sum + (l.segments.size - 1) * Config.linePartPadding)
    val spacing    = (3 * noteHeight :: l.segments.map(s => s.top.height - s.top.yAnchor + noteHeight)).max
    val textHeight = l.segments.map(_.bottom.height).maxOption getOrElse 0.0
    val yAnchor    = l.segments.map(_.yAnchor).maxOption getOrElse 0.0
    val height     = yAnchor + spacing + textHeight

    NotesLineProtoBox(width, height, yAnchor, spacing, l)

  def drawNotesLine(x: Double, y: Double, p: NotesLineProtoBox): WithHeaderInfo[List[Frag]] =
    import Config.*

    val sigPart  = drawBox(x, y, p.line.signature)
    val mainPart = drawMainPart(x + signatureWidth, y, p.spacing, p.line.segments)
    val flPart   = p.line.folioLabel.toList.flatMap(fl => drawBox(x + p.width - fl.box.width, y, fl.box))

    mainPart.map(sigPart ++ _ ++ flPart)

  final case class WithHeaderInfo[A](
      endOfLastParatext: Double,
      value: A,
  ):
    def map[B](f: A => B): WithHeaderInfo[B] = WithHeaderInfo(endOfLastParatext, f(value))

  //  first line part has staff lines ...        ... second doesn't
  // |========o=o=o===o=o========|=======|       |                           |
  // |        la-     la         |       |       |        la- la  la         |
  //          line part          line part padding      next line part
  def drawMainPart(x: Double, y: Double, spacing: Double, segments: List[LineSegment]): WithHeaderInfo[List[Frag]] =
    import Config.*

    if segments.isEmpty then WithHeaderInfo(0, Nil)
    else
      val pad = linePartPadding / 2

      final case class ParatextAccu(
          uncommited: List[Frag],
          endOfLast: Double,
          currentLanes: Int,
          maxUsedLanes: Int,
      ):
        def shiftIn(x: Double, info: ParatextInfo): (ParatextAccu, List[Frag]) =
          if x > endOfLast then
            val newAccu = ParatextAccu(List(info.frag), info.paratextEnd, 1, maxUsedLanes)
            (newAccu, uncommited)
          else
            val upShifted = uncommited.map(frag => <.g(^.transform := s"translate(0, -${info.paratextHeight})", frag))
            val newAccu   = ParatextAccu(
              upShifted :+ info.frag,
              info.paratextEnd,
              currentLanes + 1,
              Math.max(currentLanes, maxUsedLanes),
            )
            (newAccu, Nil)

      @tailrec def go(
          frags: Vector[Frag],
          x: Double,
          staffLines: Option[Double],
          prev: Option[LineSegment],
          remaining: List[LineSegment],
          pAccu: ParatextAccu,
      ): WithHeaderInfo[List[Frag]] =
        (remaining, prev, staffLines) match
          case (Nil, _, None)              => WithHeaderInfo(pAccu.endOfLast, frags.toList ++ pAccu.uncommited)
          case (Nil, _, Some(sx))          =>
            WithHeaderInfo(pAccu.endOfLast, drawStaffLines(sx, y, x - sx) ++ frags ++ pAccu.uncommited)
          case (h :: t, None, None)        =>
            val drawn                     = drawLineSegment(x, y, spacing, h)
            val (newPAccu, commitedFrags) = drawn.paratext.fold((pAccu, List.empty[Frag]))(pAccu.shiftIn(x, _))
            go(
              frags ++ drawn.normalFrags ++ commitedFrags,
              x + h.width,
              when(h.staffLines)(x),
              Some(h),
              t,
              newPAccu,
            )
          case (h :: t, Some(_), None)     =>
            val drawn                     = drawLineSegment(x + 2 * pad, y, spacing, h)
            val (newPAccu, commitedFrags) = drawn.paratext.fold((pAccu, List.empty[Frag]))(pAccu.shiftIn(x, _))
            go(
              frags ++ drawn.normalFrags ++ commitedFrags,
              x + 2 * pad + h.width,
              when(h.staffLines)(x + pad),
              Some(h),
              t,
              newPAccu,
            )
          case (_ :: _, None, Some(_))     => throw new RuntimeException("cannot draw staff lines for empty segment")
          case (h :: t, Some(_), Some(sx)) =>
            val drawn                     = drawLineSegment(x + 2 * pad, y, spacing, h)
            val (newPAccu, commitedFrags) = drawn.paratext.fold((pAccu, List.empty[Frag]))(pAccu.shiftIn(x, _))
            val lines                     = if h.staffLines then Nil else drawStaffLines(sx, y, x - sx + pad)
            go(
              lines.toVector ++ frags ++ drawn.normalFrags ++ commitedFrags,
              x + 2 * pad + h.width,
              when(h.staffLines)(sx),
              Some(h),
              t,
              newPAccu,
            )

      go(Vector.empty, x, None, None, segments, ParatextAccu(Nil, -1, 1, 1))

  def draw(grid: Grid): List[Option[String]] = grid.lines map:
    case MissingLine      => None
    case sl: SynopsisLine => Some(draw(sl))

  final case class ParatextInfo(
      frag: Frag,
      paratextEnd: Double,
      paratextHeight: Double,
  )

  final case class DrawnSegment(
      paratext: Option[ParatextInfo],
      normalFrags: List[Frag],
  )

  def drawLineSegment(x: Double, y: Double, spacing: Double, ls: LineSegment): DrawnSegment =
    val header = ls.header.map(b =>
      ParatextInfo(
        <.g(drawBox(x + ls.xAnchor, y - Config.lineTopPadding, b)),
        b.width + x + ls.xAnchor,
        b.height,
      ),
    )

    val top    = drawBox(x + ls.xAnchor, y, ls.top)
    val bottom = drawBox(x + ls.xAnchor, y + spacing + ls.bottom.yAnchor, ls.bottom)

    DrawnSegment(header, top ++ bottom)

  def drawStaffLines(x: Double, y: Double, width: Double): List[Frag] =
    (Config.lowerStaffPitch to Config.upperStaffPitch by 2)
      .map(p => drawStaffLine(x, y + (p - Config.middleStaffPitch) * Config.noteHeight / 2, width))
      .toList

  def drawBox(x: Double, y: Double, box: Box): List[Frag] =
    box match
      case nb: NotesBox                 => drawNotes(x, y, nb)
      case tb: TextBox                  => when(!tb.text.isBlank)(drawText(x, y, tb)).toList
      case MultiTextBox(tbs, _, _)      => drawMultiTextBox(x, y, tbs)
      case MultiLineTextBox(lines)      => drawMultiLineTextBox(x, y, lines)
      case CommentMarksBox(_, inner, _) => Nil ::: drawBox(x, y, inner) // todo actually draw comment marks
      case _: BlankBox                  => Nil
      case vbb: VBarBox                 => drawVBars(x, y, vbb)

  def drawMultiLineTextBox(x: Double, y: Double, lines: List[Box]): List[Frag] = lines
    .foldLeft((List.empty[Frag], y)) { case ((acc, yy), box) =>
      (acc ++ drawBox(x, yy, box), yy + box.height + Config.textLinePadding)
    }
    ._1

  def drawMultiTextBox(x: Double, y: Double, tbs: List[Box]): List[Frag] =
    tbs
      .foldLeft((List.empty[Frag], x)) { case ((l, xx), tb) =>
        (l ++ drawBox(xx, y, tb), xx + tb.width + Config.textPartPadding)
      }
      ._1

  def fontFamily(fontKind: FontKind) = fontKind match
    case FontKind.Caps   => "TextSmallCaps"
    case FontKind.Italic => "TextNormal"
    case FontKind.Normal => "TextNormal"

  def fontSize(placement: Placement) = placement match
    case Placement.Normal      => "16"
    case Placement.Subscript   => "10"
    case Placement.Superscript => "10"

  def drawText(x: Double, y: Double, text: TextBox): Frag = <.text(
    ^.x          := x + text.xAnchor,
    ^.y          := y,
    ^.fontFamily := fontFamily(text.formatting.fontKind),
    ^.fontSize   := fontSize(text.formatting.placement),
    when(text.formatting.hidden)(^.fillOpacity                             := 0.0),
    when(text.formatting.fontKind == FontKind.Italic)(^.attr("font-style") := "italic"),
    text.text,
  )

  def drawVBars(x: Double, y: Double, box: VBarBox): List[Frag] =
    box match
      case LineChangeBox  => drawVBar(x + 5, y - box.yAnchor) :: Nil
      case FolioChangeBox => drawVBar(x + 3, y - box.yAnchor) :: drawVBar(x + 7, y - box.yAnchor) :: Nil

  def drawVBar(x: Double, y: Double): Frag = <.line(
    ^.x1          := x,
    ^.y1          := y,
    ^.x2          := x,
    ^.y2          := y + Config.vBarHeight,
    ^.strokeWidth := 1.0,
    ^.stroke      := "black",
  )

  def drawStaffLine(x: Double, y: Double, width: Double): Frag = <.line(
    ^.x1          := x,
    ^.y1          := y,
    ^.x2          := x + width,
    ^.y2          := y,
    ^.strokeWidth := 1.0,
    ^.stroke      := "black",
  )

  def drawNotes(x: Double, y: Double, notes: NotesBox): List[Frag] =
    NoteRenderer.drawSpaced(x, y, notes.notes).frags.toList

  object NoteRenderer:
    import Config.*
    case class State(frags: Vector[Frag], x: Double, prevNote: Option[Note])

    def drawSpaced(x: Double, y: Double, notes: Spaced): State =
      def first(ns: NonSpaced) = ns.nonSpaced.flatMap(_.grouped.headOption).headOption

      notes.spaced.foldLeft(State(Vector.empty, x, None)):
        case (State(acc, xx, None), next)       =>
          val res = drawNonSpaced(xx, y, next)
          res.copy(frags = acc ++ res.frags)
        case (State(acc, xx, Some(prev)), next) =>
          val gap = first(next).map(Notesetting.SpacedGap.gap(prev, _)).getOrElse(0.0)
          val res = drawNonSpaced(xx + gap, y, next)
          res.copy(frags = acc ++ res.frags)

    def drawNonSpaced(x: Double, y: Double, notes: NonSpaced): State =
      def first(g: Grouped) = g.grouped.headOption

      notes.nonSpaced.foldLeft(State(Vector.empty, x, None)):
        case (State(acc, xx, None), next)       =>
          val res = drawGrouped(xx, y, next)
          res.copy(frags = acc ++ res.frags)
        case (State(acc, xx, Some(prev)), next) =>
          val gap = first(next).map(Notesetting.NonSpacedGap.gap(prev, _)).getOrElse(0.0)
          val res = drawGrouped(xx + gap, y, next)
          res.copy(frags = acc ++ res.frags)

    def drawGrouped(x: Double, y: Double, notes: Grouped): State =

      def mkNote(x: Double, n: Note): Vector[Frag] =
        val dy          = (middleStaffPitch - n.halfs) * noteHeight / 2.0
        val w           = Notesetting.noteWidth(n)
        val ledgerLines = (if dy < 0.0 then n.halfs to middleStaffPitch by -1 else n.halfs to middleStaffPitch)
          .filter(pitch => (pitch % 2 == 0) && (pitch > upperStaffPitch || pitch < lowerStaffPitch))
          .map(pitch =>
            drawStaffLine(
              x - ledgerLineJutLeft,
              y + (middleStaffPitch - pitch) * noteHeight / 2.0,
              w + ledgerLineJutLeft + ledgerLineJutRight,
            ),
          )

        (ledgerLines :+ drawNote(x, y + dy, n)).toVector

      def mkTie(x: Double, y: Double, width: Double): Frag =
        val r = noteHeight / 2.0
        <.path(
          ^.strokeLinecap := "round",
          ^.stroke        := "black",
          ^.strokeWidth   := "2",
          ^.fill          := "none",
          ^.d             := s"m $x $y v ${-r} a $r $r 0 0 1 $r ${-r} h ${width - 2 * r} a $r $r 0 0 1 $r $r v $r",
        )

      val res = notes.grouped
        .foldLeft(State(Vector.empty, x, None)):
          case (State(acc, xx, None), next)       =>
            State(acc ++ mkNote(xx, next), xx + Notesetting.noteWidth(next), Some(next))
          case (State(acc, xx, Some(prev)), next) =>
            val gap = Notesetting.GroupedGap.gap(prev, next)
            State(acc ++ mkNote(xx + gap, next), xx + gap + Notesetting.noteWidth(next), Some(next))

      if notes.grouped.size > 1 then
        val maxPitch = notes.grouped.map(_.halfs).max
        val dy       = (middleStaffPitch - (maxPitch + 3 + maxPitch % 2)) * noteHeight / 2.0
        res.copy(frags = res.frags :+ mkTie(x, y + dy, res.x - x))
      else res

    def drawNote(x: Double, yy: Double, note: Note): Frag =
      val y = yy + (note.noteType match {
        case NoteType.Flat       => -2.0
        case NoteType.Strophicus => 2.0
        case _                   => 0.0
      }) - Notesetting.noteHeight(note) / 2.0
      val s = liquescentScale

      <.use(
        ^.x         := x,
        ^.y         := y,
        when(note.liquescent)(^.transform := s"matrix($s 0 0 $s ${x - x * s} ${y - y * s})"),
        ^.xLinkHref := s"#${Templates.fromNoteType(note.noteType)}",
      )
