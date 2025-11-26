package de.olyro.monodi.exprt.print.markdown

import de.olyro.monodi.exprt.BoundingBox
import de.olyro.monodi.exprt.Linebreak
import de.olyro.monodi.exprt.Point
import de.olyro.monodi.exprt.Sub
import de.olyro.monodi.exprt.Svg
import de.olyro.monodi.exprt.TextBox
import de.olyro.monodi.exprt.print.Size
import scalatags.Text.all.*
import scalatags.Text.svgAttrs as SA
import scalatags.Text.svgTags as ST
import MdNode.*

class MdNodeRenderer(svg: Svg):
  def render(node: MdNode): List[Line] = rdr(svg.config.width, node)

  given Svg = svg

  private def rdr(width: Double, node: MdNode): List[Line] = node match
    case node: MdDocument       => node.children.flatMap(rdr(width, _))
    case node: MdText           => linebreak(width, List(defaultRun(node.text))).map(Line.Full.apply)
    case node: MdEmphasis       => linebreak(width, getRuns(node)).map(Line.Full.apply)
    case node: MdStrongEmphasis => linebreak(width, getRuns(node)).map(Line.Full.apply)
    case node: MdParagraph      => linebreak(width, getRuns(node)).map(Line.Full.apply) :+ emptyLine(paragraphYMargin)
    case node: MdHeading        => linebreak(width, getRuns(node)).map(Line.Full.apply) :+ emptyLine(headlineYMargin)
    case node: MdList           => rdrList(width, node)
    case node: MdListItem       => rdr(width, MdDocument(node.items))
    case node: MdImage          => linebreak(width, getRuns(node)).map(Line.Full.apply)
    case node: MdTable          => rdrTable(width, node)
    case node: MdRow            => linebreak(width, getRuns(node)).map(Line.Full.apply)
    case node: MdCell           => linebreak(width, getRuns(node)).map(Line.Full.apply)

  private def rdrList(width: Double, node: MdList): List[Line] =
    val sfs      = svg.config.syllableFontSize
    val dotWidth = 0.2 * sfs
    val dotBB    = BoundingBox(
      width = dotWidth,
      height = 0,
      subs = List(
        Sub(
          start = Point(0, 0),
          sub = Right(p =>
            ST.circle(
              SA.cx   := p.x.toString,
              SA.cy   := p.y.toString,
              SA.r    := (dotWidth / 2).toString,
              SA.fill := "black"
            )
          )
        )
      )
    ).addAt(TextBox.emptyBaseline, Point(0, 0.3 * sfs))

    node.items
      .map(rdr(width - listPadding - dotWidth - listDotItemSpace, _))
      .map: itemLines =>
        itemLines match
          case Nil          => Nil
          case head :: tail =>
            val firstLine  =
              BoundingBox
                .concatXAlignByFunction(List(dotBB, head.bb), listDotItemSpace, _.resolve("baseline").head.y, None)
                .padLeft(listPadding)
            val otherLines = tail.map(_.bb.padLeft(listPadding + listDotItemSpace + dotWidth))
            Line.Full(firstLine) :: otherLines.map(Line.Full(_))
      .flatten

  private def rdrTable(width: Double, node: MdTable): List[Line] =
    val rows = node.rows.collect:
      case row: MdRow => row

    val cells = rows.map: row =>
      val cells = row.children.collect:
        case cell: MdCell => cell
      cells.map(_.children)

    val columns = cells.map(_.length).maxOption.getOrElse(0)
    if columns == 0 then Nil
    else
      val cellPressures   = cells.map(row => row.map(cellPressure))
      val columnPreasures = (0 until columns).toList.map: colIdx =>
        cellPressures.map(row => row.lift(colIdx).getOrElse(minCellPressure)).maxOption.getOrElse(minCellPressure)
      val totalPressure   = columnPreasures.sum
      val freeSpace       = width - (interColumnPadding * (columns - 1))
      val columnWidths    = columnPreasures.map(p => (p / totalPressure) * freeSpace)

      def renderRow(cells: List[List[MdNode]]): Line =
        val renderedCells = cells.zipWithIndex
          .map: (content, idx) =>
            val width = columnWidths(idx)
            renderCell(width, content).bb.widenX(width)

        val rowBB = BoundingBox.concatXAlignByFunction(
          renderedCells,
          interColumnPadding,
          _.resolve("baseline").head.y,
          None
        )

        Line.Full(rowBB)

      cells.map(renderRow)

  private def minCellPressure                              = 1.0
  private def cellPressure(contents: List[MdNode]): Double =
    val width = renderCell(svg.config.width, contents).bb.width
    Math.max(minCellPressure, width)

  private def renderCell(width: Double, contents: List[MdNode]): Line =
    val lines = rdr(width, MdDocument(contents)) match
      case Nil => List(emptyLine(Size.sameAsSyllable * 0))
      case ls  => ls
    Line.Full(BoundingBox.concatY(lines.map(_.bb), 0))

  private def getRuns(node: MdNode): List[Run] = node match
    case MdDocument(children)       => children.flatMap(getRuns)
    case MdText(text)               => List(defaultRun(text))
    case MdEmphasis(children)       => children.flatMap(getRuns).map(_.inEmphasis)
    case MdStrongEmphasis(children) => children.flatMap(getRuns).map(_.inStrongEmphasis)
    case MdParagraph(children)      => children.flatMap(getRuns)
    case MdHeading(level, children) => children.flatMap(getRuns).map(_.inHeading(level))
    case MdList(_, items)           => items.flatMap(getRuns)
    case MdListItem(items)          => items.flatMap(getRuns)
    case MdTable(rows)              => rows.flatMap(getRuns)
    case MdRow(children)            => children.flatMap(getRuns)
    case MdCell(header, children)   => children.flatMap(getRuns)
    case MdImage(altText, src)      =>
      List(Run.Image(src, Size.OfPageWidth(altText.flatMap(_.toDoubleOption).getOrElse(0.3))))

  private val paragraphYMargin   = Size.sameAsSyllable
  private val headlineYMargin    = Size.sameAsSyllable * 0.5
  private val interColumnPadding = svg.config.syllableFontSize * 0.5
  private val interWordPadding   = svg.config.syllableFontSize * 0.2
  private val listPadding        = svg.config.syllableFontSize
  private val listDotItemSpace   = svg.config.syllableFontSize * 0.3

  private def emptyLine(height: Size): Line = Line.Empty(height)

  private def linebreak(width: Double, runs: List[Run]): List[BoundingBox] =
    Linebreak.break(runs, xPadding = interWordPadding, maxWidth = width)

  private def defaultRun(text: String): Run = Run.Text(text, Size.sameAsSyllable, RunStyle.Normal)
