package de.olyro.monodi.exprt
package print

final case class Page(
    content: BoundingBox,
    additionalCss: Option[String]
)
