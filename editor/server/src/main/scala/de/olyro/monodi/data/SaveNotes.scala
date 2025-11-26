package de.olyro.monodi
package data

import io.circe.*

final case class SaveNotes(
  id: String,
  notes: Json
) derives Codec.AsObject
