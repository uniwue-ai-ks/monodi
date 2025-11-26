package de.olyro.monodi
package data

import io.circe.*, io.circe.generic.semiauto.*

final case class UserInfo(
  user: User,
  roles: List[Role],
)

object UserInfo:
  given userInfoEncoder: Encoder[UserInfo] = deriveEncoder[UserInfo]
  given userInfoDecoder: Decoder[UserInfo] = deriveDecoder[UserInfo]
