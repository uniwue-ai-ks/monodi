package de.olyro.monodi
package data

import io.circe.*, io.circe.generic.semiauto.*

final case class DocumentQuery(
  dokumenten_id:            Option[String],
  gattung1:                 Option[String],
  gattung2:                 Option[String],
  festtag:                  Option[String],
  feier:                    Option[String],
  textinitium:              Option[String],
  bibliographischerverweis: Option[String],
  druckausgabe:             Option[String],
  zeilenstart:              Option[String],
  foliostart:               Option[String],
  kommentar:                Option[String],
)

object DocumentQuery:
  given documentQueryEncoder: Encoder[DocumentQuery] = deriveEncoder[DocumentQuery]
  given documentQueryDecoder: Decoder[DocumentQuery] = deriveDecoder[DocumentQuery]

