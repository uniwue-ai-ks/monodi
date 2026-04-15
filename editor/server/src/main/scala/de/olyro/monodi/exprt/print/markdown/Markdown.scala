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
  def render(
      svg: Svg,
      markdown: String,
      contentStartY: Double,
      heading: Option[BoundingBox]
  ): List[BoundingBox] =
    println("Markdown.render")
    val extensions = java.util.List.of(TablesExtension.create())
    println("Markdown.render: extensions created")
    val parser     = Parser.builder().extensions(extensions).build()
    println("Markdown.render: parser created")
    val document   = parser.parse(markdown)
    println("Markdown.render: document parsed")
    val lines      = heading.map(Line.Full(_)).toList ++ makeLines(svg, markdown, document)
    println(s"Markdown.render: ${lines.length} lines created")
    val ret        = makePages(svg, lines, contentStartY).map(_.widenX(svg.config.width))
    println("Markdown.render done")
    ret

  private def makeLines(svg: Svg, markdown: String, document: Node): List[Line] =
    println("Markdown.makeLines: AST created")
    val node  = MdNode.fromDocument(document)
    println("Markdown.makeLines: MdNode created")
    val lines = MdNodeRenderer(svg).render(node)
    println("Markdown.makeLines: lines created")
    lines

  private def makePages(svg: Svg, lines: List[Line], contentStartY: Double): List[BoundingBox] =
    given Svg = svg

    Pagebreak
      .paginate(
        lines = lines,
        yPadding = svg.config.syllableFontSize * 0.3,
        maxHeight = svg.config.height - contentStartY
      )
      .map(_.padTop(contentStartY))
