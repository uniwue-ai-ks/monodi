package de.olyro.monodi.exprt
package iiif

import java.net.URI

import de.olyro.monodi.Util.{Atomic, http}
import de.olyro.monodi.Util.http.HttpClient
import io.circe.{DecodingFailure, HCursor, Json}
import io.circe.parser.{parse, decode}
import zio.{Scope as _, *}

object Iiif:

  private val infoSuffix = "/info.json"

  private def loadManifest(manifestUri: String): ZIO[HttpClient, Throwable, Json] = for
    uri  <- ZIO.attempt(URI.create(manifestUri))
    raw  <- http.get(uri)
    json <- ZIO.fromEither(parse(raw))
  yield json

  private def getImageInfo(manifest: Json, canvasIdx: Int): Either[DecodingFailure, String] =
    HCursor
      .fromJson(manifest)
      .downField("sequences")
      .downArray
      .downField("canvases")
      .downN(canvasIdx)
      .downField("images")
      .downArray
      .downField("resource")
      .downField("service")
      .downField("@id")
      .as[String]
      .map(_ + infoSuffix)

  private val pagination = """[SsPp]\.?\s*(\d+)(?:.0)?""".r
  private val folio      = """(?:[Ff]\.?\s*)?(\d+)(?:.0)?([rv]?)""".r

  private def parseFoliostart(s: String): Option[Int] = s match
    case folio(number, side) => number.toIntOption.map(2 * _ - (if side == "v" then 0 else 1))
    case pagination(number)  => number.toIntOption
    case _                   => None

  sealed trait Manifest:
    def imageInfoForPage(pageNr: String): Either[String, String]

  type ManifestStore = StoreService

  sealed trait StoreService:
    def manifestForDocument(
        uri: String,
        foliostart: String,
        foliooffset: String
    ): ZIO[HttpClient, String, Manifest]

  def manifestForDocument(
      uri: String,
      foliostart: String,
      foliooffset: String
  ): ZIO[ManifestStore & HttpClient, String, Manifest] =
    ZIO.serviceWithZIO[ManifestStore](_.manifestForDocument(uri, foliostart, foliooffset))

  def live: ULayer[ManifestStore] = ZLayer.fromZIO(
    for cache <- Atomic.make(Map.empty[String, Json])
    yield new StoreService {
      override def manifestForDocument(
          uri: String,
          foliostart: String,
          foliooffset: String
      ): ZIO[HttpClient, String, Manifest] =
        cache
          .update(c =>
            ZIO
              .fromOption(c.get(uri))
              .map((c, _))
              .orElse(loadManifest(uri).map(m => (c + (uri -> m), m)))
              .mapError(t => s"failed to get source manifest: $t"),
          )
          .map(json =>
            new Manifest {
              override def imageInfoForPage(pageNr: String): Either[String, String] = for
                nr  <- parseFoliostart(pageNr.trim).toRight(s"`$pageNr` could not be parsed as page index")
                idx <- calculateIdx(nr, foliooffset)
                res <- getImageInfo(json, idx).left.map(_ => s"could not find page index $idx in source manifest")
              yield res
            },
          )
    }
  )

  private def calculateIdx(pageNr: Int, foliooffset: String): Either[String, Int] =
    foliooffset.toIntOption match
      case Some(offset) => Right(pageNr + offset)
      case None         =>
        for
          expr <- Expr.parseString(foliooffset)
          idx  <- Scope.run(expr, "pageNr" -> pageNr)
        yield idx

  def manifestFromIIIFJsonList(content: String, offset: String): Either[String, Manifest] =
    decode[List[String]](content).toOption match
      case None       => Left("Could not parse iiif json list")
      case Some(uris) =>
        parseFoliostart(offset) match
          case None         => Left(s"Could not parse offset $offset (inline json)")
          case Some(offset) =>
            Right(new Manifest {
              def imageInfoForPage(pageNr: String): Either[String, String] =
                parseFoliostart(pageNr) match {
                  case None         => Left(s"Could not parse page number: $pageNr (json)")
                  case Some(pageNr) =>
                    uris.lift(pageNr - offset) match {
                      case None      => Left(s"Page $pageNr not found in document (json)")
                      case Some(uri) => Right(uri)
                    }
                }
            })
