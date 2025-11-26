package de.olyro.monodi
package exprt

sealed trait CategoryFilter

final case class SourceCategoryFilter(
    statusWhiteList: List[String],
) extends CategoryFilter

final case class DocumentCategoryFilter(
    statusWhiteList: List[String],
) extends CategoryFilter
