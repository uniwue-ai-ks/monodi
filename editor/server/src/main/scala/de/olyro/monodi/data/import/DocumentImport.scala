package de.olyro.monodi
package data
package imprt

import io.circe.*, io.circe.generic.semiauto.*

final case class DocumentImport(
  meta: Document,
  document: Json
)

object DocumentImport:
  given diEncoder: Encoder[DocumentImport] = deriveEncoder[DocumentImport]
  given diDecoder: Decoder[DocumentImport] = deriveDecoder[DocumentImport]
