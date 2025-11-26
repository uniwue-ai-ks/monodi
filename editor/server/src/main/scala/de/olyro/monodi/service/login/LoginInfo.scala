package de.olyro.monodi
package service
package login

import io.circe.*, io.circe.generic.semiauto.*

final case class LoginInfo(user: String, password: String)

object LoginInfo:
  given loginInfoDecoder: Decoder[LoginInfo] = deriveDecoder[LoginInfo]
