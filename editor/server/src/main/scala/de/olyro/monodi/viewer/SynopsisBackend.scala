package de.olyro.monodi
package viewer

import cats.effect.{ExitCode, IO, IOApp}
import cats.*
import cats.data.*
import cats.implicits.*
import de.olyro.monodi.`exprt`.renderer.Renderer
import de.olyro.monodi.`exprt`.*
import de.olyro.monodi.data.BinaryData
import de.olyro.monodi.db.DBRunner
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.middleware.CORS

import scala.concurrent.duration.*
import de.olyro.monodi.data.notes.*
import io.circe.Decoder
import de.olyro.monodi.exprt.print.*
import de.olyro.monodi.exprt.print.bookbinding.*

object SynopsisBackend extends IOApp:
  val CANVAS_WIDTH_ZOOM_MIN: Double = 0.5
  val CANVAS_WIDTH_ZOOM_MAX: Double = 1.7

  final case class SynopsisQuery(
      documents: List[String],
      search: Option[String],
      singleLine: Option[Boolean],
      textInLastDocOnly: Option[Boolean]
  ) derives io.circe.Codec.AsObject

  given pdfBackendDecoder: Decoder[Either[Request, CreatePdfRequestOld]] =
    Decoder[Request].map(Left(_)).or(Decoder[CreatePdfRequestOld].map(Right(_)))

  def service(bookbinder: Bookbinder) = HttpRoutes.of[IO]:
    case req @ POST -> Root / "synopsis" =>
      for
        query       <- req.as[SynopsisQuery]
        docs        <-
          fs2.Stream
            .fromIterator[IO](query.documents.iterator, 10)
            .evalMap(id =>
              DBRunner.run(DBRunner.getDocumentNotesDecoded(fixId(id))).map(_.get._1)
            ) // fixme: add error handling
            .compile
            .toList
        filteredDocs = query.search.fold(docs)(filterString => docs.map(Filter.filter(_, filterString)))
        finalDocs    = if query.textInLastDocOnly.getOrElse(false) then reduceDocumentTexts(filteredDocs) else filteredDocs
        grid         = if query.singleLine.getOrElse(false) then Renderer.makeGrid(finalDocs) else Renderer.makeGrids(finalDocs)
        result      <- Ok(grid.map(de.olyro.monodi.`exprt`.renderer.Svg.draw))
      yield result

    case req @ POST -> Root / "makePdf" =>
      given zio.Runtime[Any] = zio.Runtime.default
      for
        query            <- req.as[Either[Request, CreatePdfRequestOld]].map(_.fold(identity, _.toNew))
        docs              = query.documents
        // somehow, zero documents hang the whole system. I fear this is some weird interaction between Cats and
        // ZIO?
        _                <- IO(if docs.isEmpty then sys.error("Can not handle zero documents"))
        exportArgs       <-
          IO.fromFuture(
            IO(
              zio.Unsafe.unsafe(unsafe ?=>
                zio.Runtime.default.unsafe.runToFuture(
                  ExportConfig.load.orDie
                ),
              )
            )
          )
        notes            <-
          fs2.Stream
            .fromIterator[IO](docs.iterator, 10)
            .evalMap(id =>
              DBRunner
                .run(DBRunner.getDocumentNotesDecoded(fixId(id)))
                .map(res => BookEntry.Document(id, res.get._1))
            ) // fixme: add error handling
            .compile
            .toList
        meta             <-
          fs2.Stream
            .fromIterator[IO](docs.iterator, 10)
            .evalMap(id =>
              DBRunner
                .run(DBRunner.getDocument(fixId(id)))
                .map(doc =>
                  BookEntry.ofMetaStrings(
                    List(
                      doc.get.textinitium,
                      doc.get.gattung1,
                      doc.get.bibliographischerverweis,
                      doc.get.dokumenten_id,
                      exportArgs.editorPseudonyms.getOrElse(doc.get.additionalData.getOrElse("Editor", "-"), "-")
                    )
                  )
                )
            ) // fixme: add error handling
            .compile
            .toList
        printViewDrawer   = Svg(query.printSettings.canvasSettings)
        printViewLayouter = Print(printViewDrawer, query.printSettings.printSettings)
        boxes            <-
          zio.interop.toEffect[IO, Any, List[BoundingBox]](
            printViewLayouter
              .drawPrintView(
                meta.zip(notes).flatMap(t => List(t._1, t._2))
              )
              .provide(ProgressNotifier.toConsole)
              .map(_.map(_.content))
          )
        pages             = boxes.map(printViewDrawer.makeSVG(_, ""))
        pdfPrinter        = new Pdf(
                              Pdf.Config(
                                marginTop = query.printSettings.marginTopInMm.toInt,
                                marginLeft = query.printSettings.marginLeftInMm.toInt,
                                marginRight = query.printSettings.marginRightInMm.toInt,
                                paperWidth = query.printSettings.paperWidthInMm,
                                paperHeight = query.printSettings.paperHeightInMm
                              )
                            )
        pdf              <-
          IO.fromFuture(IO(try {
            zio.Unsafe.unsafe(unsafe ?=>
              zio.Runtime.default.unsafe
                .runToFuture(pdfPrinter.printSvg(pages).provide(ContainerEngine.live))
            )
          } catch {
            case t: Throwable => t.printStackTrace(); throw t
          }))
        result           <- Ok(BinaryData(pdf.data))
      yield result

    case req @ POST -> Root / "makePdfQueue" =>
      given zio.Runtime[Any] = zio.Runtime.default
      for
        query       <- req.as[Either[Request, CreatePdfRequestOld]].map(_.fold(identity, _.toNew))
        queueResult <- zio.interop.toEffect[IO, Any, Option[WorkId]](bookbinder.queue(query))
        result      <- Ok(queueResult)
      yield result

    case req @ POST -> Root / "makePdfStatus" =>
      given zio.Runtime[Any] = zio.Runtime.default
      for
        query  <- req.as[WorkId]
        status <- zio.interop.toEffect[IO, Any, Status](bookbinder.getStatusAndMaybeRemove(query))
        result <- Ok(status)
      yield result

    case req @ POST -> Root / "makePdfAbort" =>
      given zio.Runtime[Any] = zio.Runtime.default
      for
        query  <- req.as[WorkId]
        status <- zio.interop.toEffect[IO, Any, Unit](bookbinder.abort(query))
        result <- Ok(status)
      yield result

  private def reduceDocumentTexts(rcs: List[RootContainer]): List[RootContainer] =
    val dropParatext = new Modifier.WithDefaults[Id[_]]:
      val mw                                     = Wrapper()
      override def prePara(c: ParatextContainer) = None

    val dropTexts = new Modifier.WithDefaults[Id[_]]:
      val mw                                = Wrapper()
      override def preLinePart(c: LinePart) = c match
        case s: Syllable    => Some(s.copy(text = ""))
        case _: LineChange  => None
        case _: FolioChange => None
        case other          => Some(other)

    val dP: RootContainer => Option[RootContainer] = Modifier.modify(dropParatext, _)
    val dT: RootContainer => Option[RootContainer] = Modifier.modify(dropTexts, _)
    val both                                       = Kleisli(dP).andThen(Kleisli(dT))

    rcs match
      case Nil => Nil
      case rcs => rcs.init.flatMap(both.run).concat(dP(rcs.last))

  override def run(args: List[String]): IO[ExitCode] =
    DBRunner.evolveAll
      *> bookbinding.Bookbinder.liveResource.use(bookbinder =>
        BlazeServerBuilder[IO]
          .bindHttp(9071, "0.0.0.0")
          .withIdleTimeout(2.minutes)
          .withHttpApp(CORS.policy.withAllowCredentials(false).withAllowOriginAll(service(bookbinder).orNotFound))
          .serve
          .compile
          .drain
          .as(ExitCode.Success)
      )

  // there are some invalid ideas of multiple lines which are not supported. This is a quick fix
  // that doesn't hurt because all ids should be UUIDs and not contain "%0A" anyway, but
  // it should work until the ids get fixed in the underlying data base
  private def fixId(s: String): String =
    s.replace("%0A", "\n")
