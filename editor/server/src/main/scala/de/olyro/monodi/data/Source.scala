package de.olyro.monodi
package data

import io.circe.*, io.circe.generic.semiauto.*

// field order must match the quelle table's column order (i.e. ALTER TABLE
// order): the SELECT * and INSERT VALUES queries map fields positionally
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
    beschreibung: String,
    cantus_siglum: String,
    cantus_century: String,
    notationsdokumentation: String
)

object Source:
  given sourceEncoder: Encoder[Source] = deriveEncoder[Source]
  given sourceDecoder: Decoder[Source] = deriveDecoder[Source]
