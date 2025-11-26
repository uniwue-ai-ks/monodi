package de.olyro.monodi
package exprt

final case class Point(
  x: Double,
  y: Double, 
):
  def moveLeft(delta: Double)  = Point(x - delta, y)
  def moveRight(delta: Double) = Point(x + delta, y)
  def moveUp(delta: Double)    = Point(x, y - delta)
  def moveDown(delta: Double)  = Point(x, y + delta)
  def move(p: Point)           = Point(x + p.x, y + p.y)

  def +(p: Point) = move(p)
  def -(p: Point) = Point(x - p.x, y - p.y)
