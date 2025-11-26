package de.olyro.monodi
package exprt

import de.olyro.monodi.exprt.Turtle.Ref

final case class CategoryDescription(
    propertyName: String,
    isNativeColumn: Boolean,
    columnName: String,
    filter: CategoryFilter,
    guard: Option[CategoryDescription.Guard]
)

object CategoryDescription:
  final case class Guard(column: String, ref: Ref)
