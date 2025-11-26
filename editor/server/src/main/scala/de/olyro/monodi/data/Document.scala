package de.olyro.monodi
package data

import io.circe.*, io.circe.generic.semiauto.*

final case class Document(
    id: String,
    quelle_id: String,
    dokumenten_id: String,
    gattung1: String,
    gattung2: String,
    festtag: String,
    feier: String,
    textinitium: String,
    bibliographischerverweis: String,
    druckausgabe: String,
    zeilenstart: String,
    foliostart: String,
    kommentar: String,
    editionsstatus: String,
    additionalData: Map[String, String],
    publish: String
)

object Document:
  given documentEncoder: Encoder[Document] = deriveEncoder[Document]
  given documentDecoder: Decoder[Document] = deriveDecoder[Document]
