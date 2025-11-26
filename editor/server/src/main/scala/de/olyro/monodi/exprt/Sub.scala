package de.olyro.monodi
package exprt

import scalatags.Text.all.*

final case class Sub(
  start: Point,
  sub: Either[BoundingBox, Point => Frag]
)
