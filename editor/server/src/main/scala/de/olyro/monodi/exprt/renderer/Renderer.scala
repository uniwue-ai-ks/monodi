package de.olyro.monodi.exprt.renderer

import cats.implicits.*
import de.olyro.monodi.exprt.FontKind
import de.olyro.monodi.exprt.renderer.Typesetting.{Formatting, Placement}
import de.olyro.monodi.data.notes.{Box as BoxLP, *}

import java.util.UUID
import scala.Option.when
import scala.annotation.tailrec

object Renderer:
  def renderLinePart(labels: LabelMap)(fontKind: FontKind)(lp: LinePart): Option[LineSegment] =
    val paratextBox = renderSingleLineModeParatext(lp, labels)
    lp match
      case LineChange(_, hasNotes, _)             => Some(LineSegment(paratextBox, Box.empty, LineChangeBox, hasNotes))
      case FolioChange(_, hasNotes, _, _)         =>
        Some(LineSegment(paratextBox, Box.empty, FolioChangeBox, hasNotes))
      case Syllable(_, text, notes, syllableType) =>
        val lso = syllableType match
          case SyllableType.Normal =>
            Some(LineSegment(paratextBox, NotesBox(notes), renderTextPart(text, fontKind), staffLines = true))
          case _                   =>
            Some(LineSegment(paratextBox, Box.empty, renderTextPart(text, fontKind), staffLines = false))
        lso.map(ls => ls.copy(width = ls.width + Config.syllablePadding, xAnchor = Config.syllablePadding / 2.0))
      case _: Clef                                => None
      case _: BoxLP                                 => None

  def renderSingleLineModeParatext(lp: LinePart, labels: LabelMap): Option[Box] =
    labels
      .get(lp.uuid)
      .map(p => TextBox(p.mkString(" ¶ "), Formatting(FontKind.Normal, hidden = false, Placement.Normal)))

  def renderTextPart(s: String, font: FontKind = FontKind.Normal, isHidden: Boolean = false): Box =
    val pattern = """(.*?)([\^_])(\w+)(.*)""".r
    val fmt     = Formatting(font, isHidden, Placement.Normal)

    @tailrec
    def go(s: String, acc: Vector[TextBox]): Vector[TextBox] = s match
      case pattern(head, "^", arg, tail) =>
        go(tail, acc :+ TextBox(head, fmt) :+ TextBox(arg, fmt.copy(placement = Placement.Superscript)))
      case pattern(head, "_", arg, tail) =>
        go(tail, acc :+ TextBox(head, fmt) :+ TextBox(arg, fmt.copy(placement = Placement.Subscript)))
      case ""                            => acc
      case _                             => acc :+ TextBox(s, fmt)

    if s.isEmpty then Box.empty
    else
      go(s, Vector.empty).filter(_.text.nonEmpty) match
        case Vector()    => Box.empty
        case Vector(one) => one
        case more        => MultiTextBox(more.head, more.tail.toList)

  def makeParatext(pc: ParatextContainer, fontKind: FontKind) =
    import scala.jdk.StreamConverters.*

    val lines = pc.text.lines.toScala(List).map(txt => renderTextPart(txt.toUpperCase, fontKind))
    MultiLineTextBox(lines)

  def getSignature(ft: FormteilContainer): Option[String] = ft.data.find(_.name == FormteilData.Signatur).map(_.data)

  def getFontKind(ft: FormteilContainer): FontKind = ft.data
    .flatMap(d =>
      when(d.name == FormteilData.Status && d.data == "Einsatzmarke")(FontKind.Caps) orElse
        when(d.name == FormteilData.Status && d.data == "Refrain")(FontKind.Italic),
    )
    .headOption getOrElse FontKind.Normal

  def getFolioLabel(ft: FormteilContainer): Option[FolioLabel] = ft.children.flatMap {
    case fc: FormteilContainer       => getFolioLabel(fc)
    case ZeileContainer(_, children) =>
      children
        .flatMap(PartialFunction.condOpt(_) { case FolioChange(uuid, _, _, text) =>
          FolioLabel(renderTextPart(s"|| ${normalizeFolioText(text)}"), uuid)
        })
        .headOption
    case p: ParatextContainer        => getParatextFolioLabel(p)
  }.headOption

  def getParatextFolioLabel(p: ParatextContainer): Option[FolioLabel] =
    val pattern = """(?s).*\(([fp].*)\)""".r
    PartialFunction.condOpt(p.text.trim) { case pattern(folio) =>
      FolioLabel(renderTextPart(s"|| $folio"), p.uuid)
    }

  // different editors write down folio changes differently. Some are putting a "f. " before it, others dont
  def normalizeFolioText(s: String) =
    if "^[0-9]+v?$".r matches s.trim then s"f. $s"
    else s

  final case class ProtoSLine(
      uuid: UUIDS,
      signature: Box,
      folioLabel: Option[FolioLabel],
      header: List[MultiLineTextBox],
      segments: List[LinePart],
      fontKind: FontKind,
  )

  sealed trait TreeWalk derives CanEqual:
    def isLeaf: Boolean
    def down: TreeWalk
    def hasSibling: Boolean
    def sibling: TreeWalk
    def shouldRender: Boolean
    def render: Option[ProtoSLine]

  case class WalkFormteil(ft: FormteilContainer, sig: String, sibling: TreeWalk) extends TreeWalk:
    override lazy val isLeaf: Boolean = ft.children.forall:
      case _: FormteilContainer                     => false
      case _: ZeileContainer | _: ParatextContainer => true

    override def down: TreeWalk = if isLeaf then WalkVoid
    else
      ft.children.foldRight(WalkVoid: TreeWalk):
        case (z: ZeileContainer, _)      =>
          throw new RuntimeException(s"ZeileContainer in non-leaf position (uuid=${z.uuid})")
        case (f: FormteilContainer, sib) => WalkFormteil(f, sig + getSignature(f).getOrElse(""), sib)
        case (p: ParatextContainer, sib) => WalkParatext(p, sig, sib)

    override def hasSibling: Boolean = sibling != WalkVoid

    override def shouldRender: Boolean = isLeaf

    override def render: Option[ProtoSLine] = when(isLeaf):
      val font = getFontKind(ft)

      val (line, header) = ft.children.partitionMap:
        case _: FormteilContainer => throw new RuntimeException("unreachable")
        case z: ZeileContainer    => Left(z.children)
        case p: ParatextContainer => Right(makeParatext(p, font))

      ProtoSLine(ft.uuid, renderTextPart(sig), getFolioLabel(ft), header, line.flatten, getFontKind(ft))

  case class WalkParatext(p: ParatextContainer, sig: String, sibling: TreeWalk) extends TreeWalk:
    override def isLeaf: Boolean       = true
    override def down: TreeWalk        = WalkVoid
    override def hasSibling: Boolean   = sibling != WalkVoid
    override def shouldRender: Boolean = true

    override def render: Option[ProtoSLine] = when(p.text.nonEmpty):
      ProtoSLine(
        p.uuid,
        renderTextPart(sig),
        getParatextFolioLabel(p),
        List(makeParatext(p, FontKind.Normal)),
        Nil,
        FontKind.Normal,
      )

  case object WalkVoid extends TreeWalk:
    override def isLeaf: Boolean            = true
    override def down: TreeWalk             = WalkVoid
    override def hasSibling: Boolean        = false
    override def sibling: TreeWalk          = WalkVoid
    override def shouldRender: Boolean      = false
    override def render: Option[ProtoSLine] = None

  private def walkRoot(rc: RootContainer): TreeWalk =
    rc.children.foldRight(WalkVoid: TreeWalk):
      case (fc: FormteilContainer, sib) => WalkFormteil(fc, getSignature(fc) getOrElse "", sib)
      case (mc: MiscContainer, sib)     => WalkFormteil(FormteilContainer(mc.uuid, mc.children, Nil), "", sib)

  // since UUIDs are stored as Strings in the containers, it is useful to provide
  // a type alias to clarify when a String is a UUID for clarity
  private type UUIDS    = String
  private type LabelMap = Map[UUIDS, List[String]]

  def makeGrids(rcs: List[RootContainer], labels: List[LabelMap] = Nil): List[Grid] =
    def go(cs: List[TreeWalk]): List[Grid] =
      val thisLevel   = if cs.exists(_.shouldRender) then List(renderSynopsis(cs.map(_.render), labels)) else Nil
      val nextLevel   = if !cs.forall(_.isLeaf) then go(cs.map(_.down)) else Nil
      val nextSibling = if cs.exists(_.hasSibling) then go(cs.map(_.sibling)) else Nil

      thisLevel ++ nextLevel ++ nextSibling

    go(rcs.map(walkRoot))

  // associates line UUIDs with preceding ParatextContainer texts
  private def groupLinesWithParas(parts: List[Either[List[LinePart], String]]): LabelMap =
    @tailrec
    def go(parts: List[Either[List[LinePart], String]], accum: List[String], map: LabelMap): LabelMap =
      parts match
        case Right(p) :: t if !p.isBlank         => go(t, p :: accum, map)
        case Left(Nil) :: t                      => go(t, accum, map)
        case Left(p :: _) :: t if accum.nonEmpty => go(t, Nil, map + (p.uuid -> accum.reverse))
        case _ :: t                              => go(t, Nil, map)
        case Nil                                 => map

    go(parts, Nil, Map.empty)

  def makeGrid(rcs: List[RootContainer]): List[Grid] =
    val params = rcs
      .map(rc => {
        val linesParts = Container
          .fold[Either[List[LinePart], String]] {
            case _: FormteilContainer => Left(Nil)
            case p: ParatextContainer => Right(p.text)
            case c: ZeileContainer    => Left(c.children)
            case _: MiscContainer     => Left(Nil)
            case _: RootContainer     => Left(Nil)
          }(rc)
          .filter { case Left(Nil) => false; case _ => true }

        val labelMap = groupLinesWithParas(linesParts)

        (
          rc.copy(
            children = List(
              FormteilContainer(
                UUID.randomUUID().toString,
                List(
                  ZeileContainer(
                    UUID.randomUUID().toString,
                    linesParts.flatMap {
                      case Left(part) => part
                      case _          => Nil
                    },
                  ),
                ),
                List.empty,
              ),
            ),
            documentType = DocumentType.Level1,
          ),
          labelMap,
        )
      })
      .unzip

    Function.tupled(makeGrids)(params)

  def renderSynopsis(lines: List[Option[ProtoSLine]], labels: List[LabelMap]): Grid =
    def split(l: List[LinePart]): List[List[LinePart]] =
      if l.isEmpty then Nil
      else
        def isSyllable(lp: LinePart) = lp match { case _: Syllable => true; case _ => false }
        l.dropWhile(!isSyllable(_))
          .foldLeft(List(l.takeWhile(!isSyllable(_)).reverse)):
            case (h :: t, x: Syllable) => List(x) :: h.reverse :: t
            case (h :: t, x)           => (x :: h) :: t
            case (Nil, _)              => throw new RuntimeException("unreachable!")
          .reverse

    val allLabels = labels.foldLeft(Map.empty: LabelMap)(_ ++ _)
    val raw       = lines map:
      case None        => Nil
      case Some(proto) => split(proto.segments).map(_.flatMap(renderLinePart(allLabels)(proto.fontKind)))

    def width(l: List[LineSegment]) =
      if l.isEmpty then 0.0 else l.map(_.width).sum + (l.size - 1) * Config.linePartPadding

    val blankPenalty = Config.linePartPadding

    @tailrec
    def go(acc: List[List[LineSegment]], rem: List[List[List[LineSegment]]]): List[List[LineSegment]] =
      val headWidths = rem.flatMap(_.headOption).map(width)
      if headWidths.isEmpty then acc.map(_.reverse)
      else
        val maxWidth = headWidths.max

        val newAcc = acc.zipAll(rem, Nil, Nil) map:
          case (l, Nil)    => l
          case (l, h :: t) =>
            val space = LineSegment.hSpace(
              maxWidth - width(h) - (if h.nonEmpty then blankPenalty else 0.0),
              (h.lastOption orElse l.headOption orElse t.headOption.flatMap(_.headOption)).exists(_.staffLines),
            )
            space :: h.reverse ++ l

        val newRem = rem.map(_.drop(1))
        go(newAcc, newRem)

    val gridLines = go(Nil, raw).zipAll(lines, Nil, None).map:
      case (segs, Some(p)) => SynopsisLine(p.signature, p.folioLabel, p.header, segs)
      case (Nil, None)     => MissingLine
      case (x, y)          => throw new RuntimeException(s"grid line mismatch: $x vs $y")

    Grid(gridLines)

  def dbg(box: Box): String = box match
    case _: NotesBox                  => "«notes»"
    case TextBox(text, _)             => text
    case MultiTextBox(boxes, _, _)    => boxes.map(dbg).mkString
    case MultiLineTextBox(boxes)      => boxes.map(dbg).mkString("+")
    case CommentMarksBox(_, inner, _) => s"`${dbg(inner)}`"
    case _: BlankBox                  => "#"
    case LineChangeBox                => "|"
    case FolioChangeBox               => "||"
