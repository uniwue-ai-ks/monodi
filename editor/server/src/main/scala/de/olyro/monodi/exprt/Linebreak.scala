package de.olyro.monodi.exprt

import scala.annotation.tailrec

object Linebreak:
  trait Run[R]:
    def baseline(b: BoundingBox): Double

    extension (r: R)
      def render: BoundingBox
      def split: Option[(R, R)]

  /**
      * Breaks a number of runs into lines that fit within the given maxWidth,
      * applying the given xPadding between runs.
      *
      * The lines are _not_ widened to the maxWidth, i.e. the bounding boxes
      * are ragged.
      */
  def break[R: Run as R](
      runs: List[R],
      xPadding: Double,
      maxWidth: Double
  ): List[BoundingBox] =
    @tailrec
    def go(
        closedLines: List[BoundingBox],
        openLine: List[BoundingBox],
        remaining: List[R]
    ): List[BoundingBox] =
      def concatX(boxes: List[BoundingBox]): BoundingBox =
        BoundingBox.concatXAlignByFunction(boxes, xPadding, R.baseline, None)

      remaining match
        case Nil          =>
          openLine match
            case Nil => closedLines.reverse
            case _   => (concatX(openLine.reverse) :: closedLines).reverse
        case head :: tail =>
          val paddings  = Math.max(0.0, xPadding * openLine.length)
          val startPos = openLine.map(_.width).sum + paddings
          val rendered = head.render
          if startPos + rendered.width <= maxWidth then go(closedLines, rendered :: openLine, tail)
          else
            head.split match
              case None                  =>
                go(concatX(openLine.reverse) :: closedLines, List(rendered), tail)
              case Some((first, second)) =>
                go(
                  closedLines,
                  openLine,
                  first :: second :: tail
                )

    go(Nil, Nil, runs)
