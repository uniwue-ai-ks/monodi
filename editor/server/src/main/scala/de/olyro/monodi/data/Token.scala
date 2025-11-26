package de.olyro.monodi
package data

import io.circe.*, io.circe.generic.semiauto.*
import java.time.*

final case class Token(user: User, issued: Instant)

object Token:
  given tokenEncoder: Encoder[Token] = deriveEncoder[Token]
  given tokenDecoder: Decoder[Token] = deriveDecoder[Token]

