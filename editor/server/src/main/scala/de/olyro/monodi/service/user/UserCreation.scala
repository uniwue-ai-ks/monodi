package de.olyro.monodi
package service
package user

import io.circe.*, io.circe.generic.semiauto.*

final case class UserCreation(user: String, password: String)

object UserCreation:
  given userCreationDecoder: Decoder[UserCreation] = deriveDecoder[UserCreation]
