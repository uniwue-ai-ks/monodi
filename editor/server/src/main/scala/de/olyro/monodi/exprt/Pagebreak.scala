package de.olyro.monodi.exprt

import scala.annotation.tailrec

object Pagebreak:
  trait Line[L]:
    extension (l: L)
      def render: BoundingBox
      def isVerticalSpace: Boolean

  def paginate[L: Line as L](
      lines: List[L],
      yPadding: Double,
      maxHeight: Double
  ): List[BoundingBox] =
    @tailrec
    def go(
        pages: List[BoundingBox],
        openPage: List[BoundingBox],
        remaining: List[L]
    ): List[BoundingBox] =
      remaining match
        case Nil          =>
          openPage match
            case Nil => pages.reverse
            case _   => (BoundingBox.concatY(openPage.reverse, yPadding) :: pages).reverse
        case head :: tail =>
          val isStartOfPage = openPage.isEmpty
          if isStartOfPage && head.isVerticalSpace then go(pages, openPage, tail)
          else
            val paddings = Math.max(0.0, yPadding * openPage.length)
            val startPos = openPage.map(_.height).sum + paddings
            val rendered = head.render
            if startPos + rendered.height <= maxHeight then go(pages, rendered :: openPage, tail)
            else
              go(
                BoundingBox.concatY(openPage.reverse, yPadding) :: pages,
                List(rendered),
                tail
              )

    go(Nil, Nil, lines)
