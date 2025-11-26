package de.olyro.monodi
package data

import doobie.*
import io.circe.*

sealed trait Role derives CanEqual

object Role:
  case object User extends Role
  case object Admin extends Role

  def parse(s: String): Option[Role] = Option(s)
    .map(_.toLowerCase.trim)
    .flatMap(_ match {
      case "user"  => Some(User)
      case "admin" => Some(Admin)
      case _       => None
    })

  given encoder: Encoder[Role] = Encoder[String].contramap[Role]({
    case Admin => "admin"
    case User  => "user"
  })

  given decoder: Decoder[Role] = Decoder[String].emap(s => parse(s) match {
    case Some(r) => Right(r)
    case None    => Left("invalid role " + s)
  })

  given meta: Meta[Role] = Meta[String].timap[Role](
    s => parse(s).getOrElse(throw new RuntimeException("invalid role: " + s)))(
    r => r.toString.toLowerCase
  )
