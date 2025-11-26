package de.olyro.monodi.exprt.print.markdown

import de.olyro.monodi.Util.intersperse
import de.olyro.monodi.exprt.BoundingBox
import de.olyro.monodi.exprt.Linebreak
import de.olyro.monodi.exprt.Pagebreak
import de.olyro.monodi.exprt.Point
import de.olyro.monodi.exprt.Sub
import de.olyro.monodi.exprt.Svg
import de.olyro.monodi.exprt.print.Size
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import scalatags.Text.all.*
import scalatags.Text.svgAttrs as SA
import scalatags.Text.svgTags as ST

object Markdown:
  def render(svg: Svg, markdown: String, contentStartY: Double): List[BoundingBox] =
    val extensions = java.util.List.of(TablesExtension.create())
    val parser     = Parser.builder().extensions(extensions).build()
    val document   = parser.parse(markdown)
    val lines      = makeLines(svg, markdown, document)
    makePages(svg, lines, contentStartY).map(_.widenX(svg.config.width))

  private def makeLines(svg: Svg, markdown: String, document: Node): List[Line] =
    def toRuns(size: Size, texts: List[MdText]): List[Run] =
      texts.map:
        case MdText.MdNormal(t)       => Run.Text(t, size, RunStyle.Normal)
        case MdText.MdEmph(t)         => Run.Text(t, size, RunStyle.Italic)
        case MdText.MdStrongEmph(t)   => Run.Text(t, size, RunStyle.SmallCaps)
        case MdText.MdImage(alt, src) =>
          val width = alt.flatMap(_.toDoubleOption).fold(Size.OfPageWidth(0.3))(Size.OfPageWidth.apply)
          Run.Image(src, width)

    val ast        = MdAst.fromDocument(document)
    val lineBlocks = ast.blocks
      .flatMap:
        case MdBlock.MdParagraph(texts) =>
          if texts.isEmpty then Nil
          else
            List:
              given Svg = svg
              Linebreak
                .break(
                  toRuns(Size.sameAsSyllable, texts),
                  xPadding = svg.config.syllableFontSize * 0.2,
                  maxWidth = svg.config.width
                )
                .map(Line.Full(_))

        case MdBlock.MdHeading(level, texts) =>
          if texts.isEmpty then None
          else
            val sizeFactor = 1.0 + (0.3 * (7 - level))
            List:
              given Svg = svg
              Linebreak
                .break(
                  toRuns(Size.OfSyllableFontSize(sizeFactor), texts),
                  xPadding = svg.config.syllableFontSize * 0.2,
                  maxWidth = svg.config.width
                )
                .map(Line.Full(_))
        case MdBlock.MdList(ordered, items)  =>
          val sfs    = Size.OfSyllableFontSize(1.0).toDouble(svg)
          val margin = 2 * sfs
          given Svg  = svg
          items.map: item =>
            val dotBaseline = BoundingBox(0, 0, Nil, Some("baseline"), full = false)
            val dotBB       = BoundingBox(
              width = sfs,
              height = 0,
              subs = List(
                Sub(
                  start = Point(sfs * 3 / 4.0, 0),
                  sub = Right(p =>
                    ST.circle(
                      SA.cx   := p.x.toString,
                      SA.cy   := p.y.toString,
                      SA.r    := (sfs * 0.1).toString,
                      SA.fill := "black"
                    )
                  )
                )
              )
            ).addAt(dotBaseline, Point(0, 0.3 * sfs))
            val xPadding    = svg.config.syllableFontSize * 0.2
            Linebreak
              .break(
                toRuns(Size.sameAsSyllable, item),
                xPadding = xPadding,
                maxWidth = svg.config.width - margin
              ) match
              case Nil          => Nil
              case head :: tail =>
                val firstLine  =
                  BoundingBox
                    .concatXAlignByFunction(List(dotBB, head), xPadding, _.resolve("baseline").head.y, None)
                    .padLeft(sfs)
                val otherLines = tail.map(_.padLeft(sfs * 2))
                Line.Full(firstLine) :: otherLines.map(Line.Full(_))

    intersperse(lineBlocks, List(Line.Empty(Size.OfSyllableFontSize(1.0)))).flatten
    val node  = MdNode.fromDocument(document)
    val lines = MdNodeRenderer(svg).render(node)
    lines

  private def makePages(svg: Svg, lines: List[Line], contentStartY: Double): List[BoundingBox] =
    given Svg = svg

    Pagebreak
      .paginate(
        lines = lines,
        yPadding = 0,
        maxHeight = svg.config.height - contentStartY
      )
      .map(_.padTop(contentStartY))
