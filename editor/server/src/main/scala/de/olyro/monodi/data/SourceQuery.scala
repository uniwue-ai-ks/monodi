package de.olyro.monodi
package data

import io.circe.*, io.circe.generic.semiauto.*

final case class SourceQuery(
  quellensigle:         Option[String],
  herkunftsregion:      Option[String],
  herkunftsort:         Option[String],
  herkunftsinstitution: Option[String],
  ordenstradition:      Option[String],
  quellentyp:           Option[String],
  bibliotheksort:       Option[String],
  bibliothek:           Option[String],
  bibliothekssignatur:  Option[String],
  kommentar:            Option[String],
  datierung:            Option[String],
)

object SourceQuery:
  given sourceQueryEncoder: Encoder[SourceQuery] = deriveEncoder[SourceQuery]
  given sourceQueryDecoder: Decoder[SourceQuery] = deriveDecoder[SourceQuery]
