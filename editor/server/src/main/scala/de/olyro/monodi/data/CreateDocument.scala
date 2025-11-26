package de.olyro.monodi
package data

import io.circe.*

final case class CreateDocument(
    document: Document,
    notes: Json
) derives Codec.AsObject
