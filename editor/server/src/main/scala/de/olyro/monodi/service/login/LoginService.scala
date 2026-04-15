package de.olyro.monodi
package service
package login

import doobie.implicits.*
import cats.effect.*, cats.implicits.*
import de.olyro.monodi.data.*
import db.*
import io.circe.*, io.circe.syntax.*
import java.time.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import pdi.jwt.*
import Result.{Ok as ROk, *}

object LoginService:
  val logoutService = HttpRoutes.of[IO]:
    case POST -> Root / "api" / "login" / "logout" => for
      result <- Ok(ROk)
    yield result

  lazy val loginService = HttpRoutes.of[IO]:
    case req@POST -> Root / "api" / "login" / "login" =>
      for
        loginInfo <- req.as[LoginInfo]
        result    <- User.parse(loginInfo.user) match
          case None    => Ok(InvalidUsernameFormat)
          case Some(u) =>
            for
              query  <- DBRunner.run(
                DBRunner.areValidCredentials(u, loginInfo.password).product(DBRunner.listRoles(u))
              )
              result <- if query._1 then createToken(u).flatMap(t => Ok(LoginSuccessful(u, query._2, t))) else  Ok(LoginFailed)
            yield result
      yield result

  private def createToken(u: User): IO[String]= for
    now    <- IO { Instant.now }
    string <- IO { encode(Token(u, now)) }
  yield string

  private def encode[A: Encoder](a: A): String = JwtCirce.encode(a.asJson, Cert.privateKey, JwtAlgorithm.RS256)
