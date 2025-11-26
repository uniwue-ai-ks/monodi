package de.olyro.monodi
import cats.effect.*, cats.data.*
import cats.implicits.*
import io.circe.*
import java.nio.file.*
import java.util.concurrent.*
import scala.concurrent.duration.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.*
import org.http4s.server.middleware.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.blaze.server.*
import scala.concurrent.ExecutionContext

import service.login.LoginService
import service.user.UserService
import service.source.SourceService
import service.document.DocumentService

object Main extends IOApp:
  val dist: java.nio.file.Path = Paths.get("dist")
  val blockingEc               = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

  val testService = HttpRoutes.of[IO] { case GET -> Root / "api" / "test" =>
    for
      select <- db.DBRunner.run(db.DBRunner.test)
      result <- Ok(Json.fromString(select.toString))
    yield result
  }

  val staticService = HttpRoutes.of[IO]:
    case req @ GET -> file => {
      val path = file.normalize.toString
      if path.isEmpty then
        StaticFile
          .fromResource("/de/olyro/monodi/static/index.html", Some(req))
          .getOrElseF(NotFound())
      else
        StaticFile
          .fromResource("/de/olyro/monodi/static/" + path, Some(req))
          .getOrElseF(NotFound())
    }

  def run(args: List[String]): IO[ExitCode] =
    db.DBRunner.evolveAll *>
      IO(println("Database initialized")) *>
      upload.Export.exprt *>
      IO(println("Exported")) *>
      BlazeServerBuilder[IO]
        .bindHttp(9070, "0.0.0.0")
        .withIdleTimeout(2.minutes)
        .withHttpApp(
          returnErrors(
            CORS.policy.withAllowOriginAll.withAllowHeadersAll(
              (
                testService <+>
                  LoginService.logoutService <+>
                  LoginService.loginService <+>
                  UserService.list <+>
                  UserService.remove <+>
                  UserService.create <+>
                  SourceService.list <+>
                  SourceService.get <+>
                  SourceService.getSigle <+>
                  SourceService.query <+>
                  SourceService.importZip <+>
                  SourceService.importDocumentExcel <+>
                  SourceService.importSourceExcel <+>
                  SourceService.deleteDocumentExcel <+>
                  SourceService.deleteSourceExcel <+>
                  DocumentService.list <+>
                  DocumentService.update <+>
                  DocumentService.get <+>
                  DocumentService.query <+>
                  DocumentService.getNotes <+>
                  DocumentService.saveNotes <+>
                  DocumentService.verifyNotes <+>
                  staticService
              ).orNotFound)
              )
            )
        .serve
        .compile
        .drain
        .as(ExitCode.Success)

  def returnErrors[F[_]: Sync](k: Kleisli[F, Request[F], Response[F]]): Kleisli[F, Request[F], Response[F]] =
    Kleisli(req =>
      k(req).attempt.flatMap({
        case Left(e)  => {
          Http4sDsl[F].http4sInternalServerErrorSyntax(InternalServerError).apply(Util.stringify(e))
        }
        case Right(r) => r.pure[F]
      }),
    )
