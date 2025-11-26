package de.olyro.monodi
package data

import doobie.*
import io.circe.*

sealed abstract case class User(mail: String) derives CanEqual

object User:

  def parse(s: String): Option[User] = Option(s)
    .map(_.toLowerCase.trim)
    .filter(Util.isMailAddress)
    .map(new User(_) {})

  def unsafe(s: String) = new User(s) {}

  given encoder: Encoder[User] = Encoder[String].contramap[User](_.mail)
  given decoder: Decoder[User] = Decoder[String].emap(s => parse(s) match {
    case Some(m) => Right(m)
    case None    => Left("invalid mail " + s)
  })

  given meta: Meta[User] = Meta[String].timap[User](
    u => parse(u).getOrElse(throw new RuntimeException("invalid user: " + u)))(
    u => u.mail
  )
