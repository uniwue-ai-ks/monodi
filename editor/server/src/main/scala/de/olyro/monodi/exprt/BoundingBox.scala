package de.olyro.monodi
package exprt

import cats.data.*
import cats.implicits.*
import scalatags.Text.all.*
import scalatags.Text.{svgAttrs as SA, svgTags as ST}

import scala.collection.immutable.Nil

final case class BoundingBox(
    width: Double,
    height: Double,
    subs: List[Sub],
    id: Option[String] = None,
    scale: Option[Double] = None,
    full: Boolean = false
):
  require(width >= 0, s"Width has to be >= 0, but was $width")
  require(height >= 0, s"height has to be >= 0, but was $height")

  def resolve(identifier: String): List[Point] =
    (if id contains identifier then List(Point(0, 0)) else List.empty[Point]) ++
      subs.flatMap(sub =>
        sub.sub match {
          case Left(bb) => bb.resolve(identifier).map(_ + sub.start)
          case Right(_) => Nil
        },
      )

  def getAllFull: List[Rect] =
    (if full then List(Rect.create(Point(0, 0), Point(width, height))) else List.empty[Rect]) ++
      subs.flatMap(sub =>
        sub.sub match {
          case Left(bb) => bb.getAllFull.map(_.moveBy(sub.start))
          case Right(_) => Nil
        },
      )

  def shrinkToFullY: BoundingBox =
    val fullRect = getAllFull.combineAllOption.getOrElse(Rect.zero)
    padTop(-fullRect.upperLeft.y).copy(height = fullRect.height)

  def widenX(minWidth: Double)  = copy(width = Math.max(width, minWidth))
  def widenY(minHeight: Double) = copy(height = Math.max(height, minHeight))

  def centerY(center: Double, id: Option[String] = None) =
    if center < height / 2 then this
    else BoundingBox(width, center + height / 2, List(Sub(Point(0, center - height / 2), Left(this))), id)

  def centerX(center: Double, id: Option[String] = None) =
    if center < width / 2 then this
    else BoundingBox(center + width / 2, height, List(Sub(Point(center - width / 2, 0), Left(this))), id)

  def addAt(bb: BoundingBox, p: Point, atFront: Boolean = false): BoundingBox =
    val sub     = Sub(p, Left(bb))
    val newSubs = if atFront then sub :: subs else subs :+ sub
    this
      .widenX(p.x + bb.width)
      .widenY(p.y + bb.height)
      .copy(subs = newSubs)

  def addAtId(bb: BoundingBox, id: String, fallback: Point, atFront: Boolean = false): BoundingBox =
    this.addAt(bb, this.resolve(id).headOption.getOrElse(fallback), atFront)

  def padTop(padding: Double): BoundingBox =
    copy(subs = subs.map(s => s.copy(start = s.start.moveDown(padding))), height = height + padding)

  def padLeft(padding: Double): BoundingBox =
    copy(subs = subs.map(s => s.copy(start = s.start.moveRight(padding))), width = width + padding)

  def padRight(padding: Double): BoundingBox =
    this.copy(width = width + padding)

  def padBottom(padding: Double): BoundingBox =
    this.copy(height = height + padding)

  def scaleWith(d: Double): BoundingBox =
    copy(scale = Some(scale.fold(d)(_ * d)), width = width * d, height = height * d)

  def hideFromLayout: BoundingBox = copy(full = false, width = 0, height = 0)

object BoundingBox:
  def concatX(l: List[BoundingBox], padding: Double, id: Option[String] = None): BoundingBox =
    if l.isEmpty then BoundingBox(0, 0, Nil)
    else
      val newWidth  = l.map(_.width).sum + padding * (l.length - 1)
      val newHeight = l.map(_.height).max

      val subs = l
        .traverse(bb =>
          for
            lastEnd <- State.get[Double]
            _       <- State.set(lastEnd + padding + bb.width)
          yield Sub(Point(lastEnd + padding, 0), Left(bb)),
        )
        .runA(-padding)
        .value

      BoundingBox(newWidth, newHeight, subs, id)

  def concatXAlignByFunction(
      l: List[BoundingBox],
      padding: Double,
      f: BoundingBox => Double,
      id: Option[String] = None
  ): BoundingBox =
    if l.isEmpty then BoundingBox(0, 0, Nil)
    else
      val newWidth       = l.map(_.width).sum + padding * (l.length - 1)
      val heighestHeight = l.map(b => f(b)).max
      val newHeight      = l.map(b => heighestHeight + b.height - f(b)).max

      val subs = l
        .traverse(bb =>
          for
            lastEnd <- State.get[Double]
            _       <- State.set(lastEnd + padding + bb.width)
          yield Sub(Point(lastEnd + padding, heighestHeight - f(bb)), Left(bb)),
        )
        .runA(-padding)
        .value

      BoundingBox(newWidth, newHeight, subs, id)

  def concatXF[A](
      l: List[A]
  )(draw: A => BoundingBox, padding: (A, A) => Double, id: Option[String] = None): BoundingBox =
    def go(accu: BoundingBox, last: Option[A], l: List[A]): BoundingBox =
      (l, last) match
        case (Nil, _)              => accu
        case (a :: as, None)       => go(accu.addAt(draw(a), Point(0, 0)), Some(a), as)
        case (a :: as, Some(last)) => go(accu.addAt(draw(a), Point(accu.width + padding(last, a), 0)), Some(a), as)

    go(BoundingBox(0, 0, Nil, id), None, l)

  def wrapX(bbs: List[BoundingBox], xPadding: Double, xMax: Double, xIndent: Double): List[BoundingBox] =
    def addOne(accu: List[BoundingBox], bb: BoundingBox): List[BoundingBox] =
      accu match
        case Nil                    => List(bb)
        case stack @ (head :: tail) =>
          if head.width + xPadding + bb.width < xMax then head.addAt(bb, Point(head.width + xPadding, 0)) :: tail
          else bb.padLeft(xIndent) :: stack

    bbs.foldLeft(List.empty[BoundingBox])(addOne).reverse

  def concatY(l: List[BoundingBox], padding: Double, id: Option[String] = None): BoundingBox =
    if l.isEmpty then BoundingBox(0, 0, Nil)
    else
      val newWidth  = l.map(_.width).max
      val newHeight = Math.max(0, l.map(_.height).sum + padding * (l.length - 1))

      val subs = l
        .traverse(bb =>
          for
            lastEnd <- State.get[Double]
            _       <- State.set(lastEnd + padding + bb.height)
          yield Sub(Point(0, lastEnd + padding), Left(bb)),
        )
        .runA(-padding)
        .value

      BoundingBox(newWidth, newHeight, subs, id)

  def concatYFull(l: List[BoundingBox], padding: Double, id: Option[String] = None): BoundingBox =

    def add(upper: BoundingBox, lower: BoundingBox): BoundingBox =
      val u         = upper.shrinkToFullY
      val lowerFull = lower.getAllFull

      val heighestIntersectionY = lowerFull.foldLeft(lower.height)((h, r) => {
        val intersectX = r.upperLeft.x < u.width
        if intersectX then h `min` r.upperLeft.y
        else h
      })

      val lowerStartPos = (u.height - heighestIntersectionY + padding) `max` 0

      lower.padTop(lowerStartPos).addAt(upper, Point(0, lowerStartPos + heighestIntersectionY - u.height - padding))

    l match
      case Nil          => BoundingBox(0, 0, Nil, id = id)
      case head :: next => next.foldLeft(head)(add)

  def draw(bb: BoundingBox, start: Point = Point(0, 0)): Frag =
    val transform = bb.scale.fold("")(s =>
      s"translate(${start.x.toString}, ${start.y.toString}) scale(${s.toString}) translate(-${start.x.toString}, -${start.y.toString})",
    )
    ST.g(SA.`class` := bb.id.getOrElse(""), SA.transform := transform)(
      bb.subs.map(sub =>
        sub.sub match {
          case Left(bb)   => draw(bb, start + sub.start)
          case Right(end) => end(start + sub.start)
        },
      )
    )

  /**
    * given a Box [A] and a space of
    * [.........], it is turned into
    * [.A.A.A.A.]. If there isn't enough space for a single repetion, the space will
    * just be filled by empty space.
    */
  def repeatToWidth(bb: BoundingBox, width: Double, minPadding: Double): BoundingBox =
    val repetitions = (Math.max(0, (width - minPadding)) / (bb.width + minPadding)).toInt
    val padding     = (width - repetitions * bb.width) / (repetitions + 1)
    val paddingBox  = empty.widenX(padding).widenY(bb.height)

    if repetitions == 0 then empty.widenY(bb.height).widenX(width)
    else
      val boxWithPadding = concatX(List(bb, paddingBox), 0)
      val repeatedBoxes  = concatX(List.fill(repetitions)(boxWithPadding), 0)
      concatX(List(paddingBox, repeatedBoxes), 0)

  /**
      * Draws a border "around" the bounding box. "Around" is in qoutes because the border
      * is actually drawn inside the bounding box. So it doesn't increase the width or height
      * of the bounding box.
      */
  def borderBox(bb: BoundingBox, padding: Double): BoundingBox =
    val rect =
      BoundingBox(
        width = bb.width + 2 * padding,
        height = bb.height + 2 * padding,
        subs = List(
          Sub(
            start = Point(0.5, 0.5),
            sub = Right(p =>
              ST.rect(
                SA.x           := p.x,
                SA.y           := p.y,
                SA.width       := bb.width - 1 + 2 * padding,
                SA.height      := bb.height - 1 + 2 * padding,
                SA.stroke      := "black",
                SA.fill        := "none",
                SA.strokeWidth := 1
              ),
            )
          )
        )
      )

    rect.addAt(bb, Point(padding, padding))

  /**
      * "Underlines" the bounding box. "Underlines" is in qoutes because the bottom-border
      * is actually drawn inside the bounding box. So it doesn't increase the width or height
      * of the bounding box.
      */
  def underlineBox(bb: BoundingBox, padding: Double): BoundingBox =
    val rect =
      BoundingBox(
        width = bb.width + 2 * padding,
        height = bb.height + 2 * padding,
        subs = List(
          Sub(
            start = Point(0.5, 0.5),
            sub = Right(p =>
              ST.line(
                SA.x1           := p.x,
                SA.y1           := p.y + bb.height - 1 + 2 * padding,
                SA.x2           := p.x + bb.width - 1 + 2 * padding,
                SA.y2           := p.y + bb.height - 1 + 2 * padding,
                SA.stroke      := "black",
                SA.fill        := "none",
                SA.strokeWidth := 1
              ),
            )
          )
        )
      )

    rect.addAt(bb, Point(padding, padding))

  lazy val empty = BoundingBox(0, 0, Nil)

extension (bb: BoundingBox) def withBorder: BoundingBox = BoundingBox.borderBox(bb, 0)
