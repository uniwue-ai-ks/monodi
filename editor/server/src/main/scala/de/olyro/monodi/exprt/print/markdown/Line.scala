package de.olyro.monodi.exprt.print.markdown

import de.olyro.monodi.exprt.print.Size
import de.olyro.monodi.exprt.BoundingBox
import de.olyro.monodi.exprt.Pagebreak
import de.olyro.monodi.exprt.Svg
import de.olyro.monodi.exprt.TextBox
import de.olyro.monodi.exprt.Point

enum Line:
  case Empty(height: Size)
  case Full(box: BoundingBox)

  def bb(using svg: Svg): BoundingBox = this match
    case Line.Empty(height) => BoundingBox
      .empty
      .widenY(height.toDouble(svg))
      .addAt(TextBox.emptyBaseline, Point(0, Size.OfSyllableFontSize(0.3).toDouble(svg)))

    case Line.Full(box)    => box

object Line:
  given (svg: Svg) => Pagebreak.Line[Line]:
    extension (line: Line)
      def render: BoundingBox = line.bb

      def isVerticalSpace: Boolean = line match
        case _: Line.Empty => true
        case _: Line.Full  => false
