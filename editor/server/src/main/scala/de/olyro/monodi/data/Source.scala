package de.olyro.monodi
package data

import io.circe.*, io.circe.generic.semiauto.*

final case class Source(
    id: String,
    quellensigle: String,
    herkunftsregion: String,
    herkunftsort: String,
    herkunftsinstitution: String,
    ordenstradition: String,
    quellentyp: String,
    bibliotheksort: String,
    bibliothek: String,
    bibliothekssignatur: String,
    kommentar: String,
    datierung: String,
    status: String,
    jahrhundert: String,
    manifest: String,
    foliooffset: String,
    publish: String,
    beschreibung: String
)

object Source:
  given sourceEncoder: Encoder[Source] = deriveEncoder[Source]
  given sourceDecoder: Decoder[Source] = deriveDecoder[Source]
