package de.olyro.monodi
package exprt

import cats.kernel.Semigroup

sealed abstract case class Rect(
    upperLeft: Point,
    lowerRight: Point,
):
  def moveBy(p: Point): Rect =
    Rect.create(upperLeft + p, lowerRight + p)

  def combine(b: Rect): Rect =
    Rect.create(
      Point(upperLeft.x min b.upperLeft.x, upperLeft.y min b.upperLeft.y),
      Point(lowerRight.x max b.lowerRight.x, lowerRight.y max b.lowerRight.y),
    )

  def height: Double = lowerRight.y - upperLeft.y
  def width: Double  = lowerRight.x - upperLeft.x
  def pad(padding: Double): Rect =
    Rect.create(upperLeft - Point(padding, padding), lowerRight + Point(padding, padding))

object Rect:
  given RectMonoid: Semigroup[Rect]:

    def combine(a: Rect, b: Rect): Rect = a.combine(b)

  def create(p1: Point, p2: Point): Rect =
    val top    = p1.y min p2.y
    val bottom = p1.y max p2.y

    val left  = p1.x min p2.x
    val right = p1.x max p2.x

    new Rect(upperLeft = Point(left, top), lowerRight = Point(right, bottom)) {}

  def zero = create(Point(0, 0), Point(0, 0))
