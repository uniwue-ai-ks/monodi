package de.olyro.monodi
package exprt

import de.olyro.monodi.data.notes.Comment

final case class Line(
    signatures: List[String],
    //data: Either[ParatextContainer, LineRepr],
    font: FontKind,
    hierarchy: List[Int],
    text: String,
    comments: List[Comment]
)
