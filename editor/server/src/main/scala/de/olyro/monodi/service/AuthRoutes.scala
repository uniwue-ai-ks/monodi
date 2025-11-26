package de.olyro.monodi
package service

import Result.{Ok as _, *}
import cats.data.*, cats.implicits.*, cats.effect.*
import data.*
import db.*
import io.circe.*
import java.security.*, java.security.cert.*
import java.time.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import pdi.jwt.*
import scala.util.*

object AuthRoutes:
  def ofRole(r: Role)(p: PartialFunction[Request[IO], User => IO[Response[IO]]]): HttpRoutes[IO] =
    Kleisli[OptionT[IO, *], Request[IO], Response[IO]](req => {
      val checks: EitherT[IO, Response[IO], User] = for
        authHeader <- getAuthorization(req)
        token      <- decodeToken(authHeader)
        _          <- checkTime(token)
        _          <- checkRole(token.user, r)
      yield token.user

      p.lift(req) match {
        case None    => OptionT((None: Option[Response[IO]]).pure[IO])
        case Some(f) => OptionT(checks.semiflatMap(f).merge.map(v => Option(v)))
      }
    })

  def ofUser(p: PartialFunction[Request[IO], User => IO[Response[IO]]]): HttpRoutes[IO] =
    Kleisli[OptionT[IO, *], Request[IO], Response[IO]](req => {
      val checks: EitherT[IO, Response[IO], User] = for
        authHeader <- getAuthorization(req)
        token      <- decodeToken(authHeader)
        _          <- checkTime(token)
      yield token.user

      p.lift(req) match {
        case None    => OptionT((None: Option[Response[IO]]).pure[IO])
        case Some(f) => OptionT(checks.semiflatMap(f).merge.map(v => Option(v)))
      }
    })

  val loginRequired: IO[Response[IO]]           = Ok(LoginRequired)
  val insufficientPermissions: IO[Response[IO]] = Ok(InsufficientPermissions)

  def getAuthorization(req: Request[IO]): EitherT[IO, Response[IO], String] =
    req.headers.get(org.typelevel.ci.CIString("Authorization")) match
      case Some(h) => EitherT.pure(h.head.value)
      case None    => EitherT.left(loginRequired)

  def decodeToken(authHeaderValue: String): EitherT[IO, Response[IO], Token] =
    decode[Token](authHeaderValue) match
      case Left(_)  => EitherT.left(loginRequired)
      case Right(t) => EitherT.pure(t)

  def checkTime(t: Token): EitherT[IO, Response[IO], Unit] = EitherT(for
    now    <- IO({ Instant.now })
    days    = java.time.temporal.ChronoUnit.DAYS.between(t.issued, now)
    result <- if days > 10 then loginRequired.map(l => Left(l)) else Right(()).pure[IO]
  yield result)

  def checkRole(u: User, r: Role): EitherT[IO, Response[IO], Unit] = EitherT(for
    roles  <- DBRunner.run(DBRunner.listRoles(u))
    result <- if roles.contains(r) then Right(()).pure[IO] else insufficientPermissions.map(l => Left(l))
  yield result)

  def decode[A: Decoder](s: String): Either[Throwable, A] =
    JwtCirce.decodeJson(s, publicKey, List(JwtAlgorithm.RS256)) match
      case Success(v) => Decoder[A].decodeJson(v)
      case Failure(e) => Left(e)

  private val publicKey: PublicKey =
    val cf   = CertificateFactory.getInstance("X.509");
    val cert = Util.unsafeWithResource(
      getClass,
      "/de/olyro/monodi/certificate/certificate.pem",
      cf.generateCertificate(_).asInstanceOf[X509Certificate],
    )
    cert.getPublicKey

