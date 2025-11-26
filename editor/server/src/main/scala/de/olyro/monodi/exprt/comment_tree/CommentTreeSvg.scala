package de.olyro.monodi
package exprt
package comment_tree

import de.olyro.monodi.data.notes.*
import scalatags.Text.all.*
import scalatags.Text.{svgAttrs as SA, svgTags as ST}
import de.olyro.monodi.data.notes.CommentTree.*
import de.olyro.monodi.data.notes.CommentTreeLeafContent.*
import java.util.UUID
import de.olyro.monodi.data.notes.Justification.Center
import cats.implicits.*
import PartiallyDrawn.*

/** This class generates SVGs for comment trees according to the layout specification in the documentation folder in the
  * project root.
  */
class CommentTreeSvg(svg: Svg):
  def draw(tree: CommentTree, comment: Option[Comment], doc: RootContainer): BoundingBox =
    drawTree(tree, comment, doc) match
      case pd @ PartiallyDrawn.UndrawnBracket(_) => drawBracket(pd.bracketInfo, UUID.randomUUID().toString)
      case PartiallyDrawn.DrawnNotes(_, bb)      => bb
      case PartiallyDrawn.DrawnText(_, bb)       => bb
      case PartiallyDrawn.DrawnOpaque(_, bb)     => bb

  private def drawTree(tree: CommentTree, comment: Option[Comment], doc: RootContainer): PartiallyDrawn =
    tree match
      case grid: CommentTreeGrid   => drawGrid(grid, comment, doc)
      case leaf: CommentTreeLeaf   => drawLeaf(leaf, comment, doc)
      case _: CommentTreeUndecided => PartiallyDrawn.DrawnOpaque(None, undecided)

  private def drawGrid(grid: CommentTreeGrid, comment: Option[Comment], doc: RootContainer): PartiallyDrawn =
    if grid.items.isEmpty || grid.items.head.isEmpty then PartiallyDrawn.DrawnOpaque(None, BoundingBox.empty)
    else
      val partials  = grid.items.map(_.map(drawTree(_, comment, doc)))
      val colWidths = partials.transpose.map(_.map(_.width).max)
      PartiallyDrawn.DrawnOpaque(
        grid.justification,
        BoundingBox.concatY(partials.map(drawRow(colWidths, _)), CommentTreeSvg.gridRowGap)
      )

  private def drawRow(colWidths: Seq[Double], partials: Seq[PartiallyDrawn]): BoundingBox =
    val baselineId  = UUID.randomUUID().toString
    val bracketInfo = partials.map(_.bracketInfo).maxBy(_.priority)
    val complete    = partials.zipWithIndex.map({ case (pd, i) =>
      expand(
        drawCompletely(pd, bracketInfo, baselineId),
        pd.justification.getOrElse(Justification.Center),
        colWidths(i)
      )
    })
    BoundingBox.concatXAlignByFunction(
      complete.toList,
      CommentTreeSvg.gridColumnGap,
      bb => bb.resolve(baselineId).head.y
    )

  private def expand(bb: BoundingBox, justification: Justification, width: Double): BoundingBox =
    justification match
      case Center              => bb.centerX(width / 2.0).widenX(width)
      case Justification.Left  => bb.widenX(width)
      case Justification.Right => bb.padLeft(width - bb.width)

  private def drawCompletely(pd: PartiallyDrawn, bi: BracketInfo, baselineId: String): BoundingBox =
    pd match
      case UndrawnBracket(_) => drawBracket(bi, baselineId)

      case PartiallyDrawn.DrawnNotes(_, bb) =>
        bb.resolve("line-marker-32").headOption match
          case None    => CommentTreeSvg.addBaseline(bb, bb.height / 2, baselineId)
          case Some(p) => CommentTreeSvg.addBaseline(bb, p.y, baselineId)

      case DrawnText(_, bb) =>
        val baseline = bb.resolve("baseline").head.y
        CommentTreeSvg.addBaseline(bb, baseline, baselineId)

      case DrawnOpaque(_, bb) =>
        val baseline = bb.height / 2
        CommentTreeSvg.addBaseline(bb, baseline, baselineId)

  private def drawLeaf(l: CommentTreeLeaf, comment: Option[Comment], doc: RootContainer): PartiallyDrawn =
    l.content match
      case _: Bracket => PartiallyDrawn.UndrawnBracket(l.justification)
      case n: Notes   => PartiallyDrawn.DrawnNotes(l.justification, drawNotes(n, comment, doc))
      case t: Text    =>
        val oneXOneBox = BoundingBox(1, 1, Nil)
        if t.content.isBlank then PartiallyDrawn.DrawnOpaque(l.justification, oneXOneBox)
        else PartiallyDrawn.DrawnText(l.justification, CommentTreeSvg.markupText(t.content, svg.config))

  private def drawNotes(n: Notes, comment: Option[Comment], doc: RootContainer): BoundingBox =
    val (prefix, suffix) = (n.context.getOrElse(false), comment) match
      case (true, None)          => (Nil, Nil)
      case (false, _)            => (Nil, Nil)
      case (true, Some(comment)) =>
        ContextFinder.find(Container.toLineParts(doc), comment.startUUID, comment.endUUID) match
          case Left(error) => println(error); (Nil, Nil)
          case Right(ctx)  => (ctx.prefix, ctx.suffix)

    svg.drawLineParts(prefix ++ n.content.children ++ suffix, FontKind.Normal, Nil).shrinkToFullY

  def drawBracket(info: BracketInfo, baselineId: String): BoundingBox =
    val bracketHeight = info.bracketHeight + 2 * BracketInfo.bracketExtraHeightTopAndBottom
    val baseline      = info.bracketInnerTopToBaseline + BracketInfo.bracketExtraHeightTopAndBottom
    val baselineSubs  = List(Sub(Point(0, baseline), Left(BoundingBox(0, 0, Nil, Some(baselineId)))))

    BoundingBox(
      width = BracketInfo.bracketWidth,
      height = bracketHeight,
      subs = baselineSubs ++ List(
        Sub(
          start = Point(0, 0),
          sub = Right(p =>
            ST.path(
              SA.d           := s"""
                        M ${p.x + BracketInfo.bracketStrokeWidth / 2} ${p.y + BracketInfo.bracketStrokeWidth / 2}
                        h ${BracketInfo.bracketWidth - BracketInfo.bracketStrokeWidth}
                        v ${bracketHeight - BracketInfo.bracketStrokeWidth}
                        h ${-BracketInfo.bracketWidth + BracketInfo.bracketStrokeWidth}
                      """,
              SA.fill        := "none",
              SA.stroke      := "black",
              SA.strokeWidth := BracketInfo.bracketStrokeWidth
            ),
          )
        )
      )
    )

  private lazy val undecided =
    BoundingBox(
      width = 100,
      height = 100,
      subs = List(
        Sub(
          start = Point(1, 1),
          sub = Right(p =>
            ST.rect(
              SA.x           := p.x,
              SA.y           := p.y,
              SA.width       := 98,
              SA.height      := 98,
              SA.stroke      := "black",
              SA.fill        := "none",
              SA.strokeWidth := 2
            ),
          )
        ),
        Sub(
          start = Point(50, 50),
          sub = Right(p =>
            ST.text(
              SA.x                := p.x,
              SA.y                := p.y,
              SA.textAnchor       := "middle",
              SA.dominantBaseline := "central",
              "??"
            ),
          )
        )
      )
    )

object CommentTreeSvg extends zio.ZIOAppDefault:
  private val gridRowGap: Double    = 20.0
  private val gridColumnGap: Double = 10.0

  import zio.*
  import java.nio.file.*

  val outputPath =
    Paths.get("/var/www/html/monodicum/svgs/comment-tree-test.svg")

  def markupText(text: String, config: CanvasConfig): BoundingBox =
    sealed trait Token derives CanEqual
    final case class TextToken(text: String) extends Token

    sealed trait Style   extends Token
    case object Normal   extends Style
    case object Caps     extends Style
    case object Bordered extends Style

    def nonEmptyToken(t: Token): Boolean =
      t match
        case TextToken(text) => text.nonEmpty
        case _               => true

    val tokenRegex = raw"""\(\(|\)\)|\[\[|\]\]|\{\{|\}\}""".r

    def tokenize(text: String): List[Token] =
      tokenRegex.findFirstMatchIn(text) match
        case None    => List(TextToken(text))
        case Some(m) =>
          val token = m.matched match
            case "((" | "))" => Normal
            case "[[" | "]]" => Caps
            case "{{" | "}}" => Bordered
          TextToken(m.before.toString()) :: token :: tokenize(m.after.toString)

    val tokens = tokenize(text).filter(nonEmptyToken)

    def italic(text: String): BoundingBox =
      TextBox.italic(text, config.useSystemFontNames, None, true)

    def normal(text: String): BoundingBox =
      TextBox.normal(text, config.useSystemFontNames, None, true)

    def caps(text: String): BoundingBox =
      TextBox.smallCaps(text, config.useSystemFontNames, None, true)

    def bordered(text: String): BoundingBox =
      BoundingBox.borderBox(TextBox.normal(text, config.useSystemFontNames, None, true), 3)

    def makeBoxes(tokens: List[Token], style: Option[Style]): List[BoundingBox] =
      tokens match
        case Nil       => Nil
        case t :: tail =>
          t match
            case TextToken(text) =>
              val current = style match
                case None           => italic(text)
                case Some(Normal)   => normal(text)
                case Some(Caps)     => caps(text)
                case Some(Bordered) => bordered(text)

              current :: makeBoxes(tail, style)
            case s: Style        =>
              val nextStyle = if style.contains(s) then None else Some(s)
              makeBoxes(tail, nextStyle)

    val boxes = makeBoxes(tokens, None)

    BoundingBox.concatXAlignByFunction(
      boxes,
      0,
      bb => bb.resolve("baseline").head.y,
      None
    )

  def run: ZIO[Any, Nothing, Int] =
    (for
      decoded   <- db.DBRunner
                     .runZ(db.DBRunner.getDocumentNotesDecoded("685bc05d-ab23-4ccc-92e3-be2e77823a13"))
                     .someOrFailException
                     .orDie
      (notes, _) = decoded
      comments   =
        Container
          .fold[List[Comment]]({
            case _: ParatextContainer => Nil
            case z: ZeileContainer    => Container.getIds(z).flatMap(id => notes.comments.filter(c => c.startUUID == id))
            case _: FormteilContainer => Nil
            case _: MiscContainer     => Nil
            case _: RootContainer     => Nil
          })(notes)
          .flatten
      bb         = new CommentTreeSvg(Svg.defaultSvg).draw(comments(0).tree.get, None, notes)
      svg        = de.olyro.monodi.exprt.DrawnNotes.make(Svg.defaultSvg)(bb)
      _         <- ZIO.succeed(Files.writeString(outputPath, svg.svg))
    yield 0).provide(db.DBRunner.live.orDie)

  private def addBaseline(bb: BoundingBox, y: Double, id: String): BoundingBox =
    bb.addAt(BoundingBox(0, 0, Nil, Some(id)), Point(0, y))
