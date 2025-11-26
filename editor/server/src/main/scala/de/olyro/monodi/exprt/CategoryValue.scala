package de.olyro.monodi.exprt

import de.olyro.monodi.exprt.Turtle.Ref

final case class CategoryValue(
    value: String,
    guard: Option[CategoryValue.Guard]
)

object CategoryValue:
  final case class Guard(attribute: Ref, value: String)
