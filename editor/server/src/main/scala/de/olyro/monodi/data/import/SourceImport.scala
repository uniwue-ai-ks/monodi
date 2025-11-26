package de.olyro.monodi
package data
package imprt

import io.circe.*, io.circe.generic.semiauto.*

final case class SourceImport(
  meta: Source,
  documents: List[DocumentImport]
)

object SourceImport:
  given siEncoder: Encoder[SourceImport] = deriveEncoder[SourceImport]
  given siDecoder: Decoder[SourceImport] = deriveDecoder[SourceImport]
