package de.olyro.monodi
package upload
package zip

import alleycats.std.map.*
import cats.*, cats.data.*, Validated.*, cats.implicits.*, cats.effect.*
import scala.jdk.CollectionConverters.*
import java.util.Scanner
import java.nio.file.*
import java.util.zip.*
import de.olyro.monodi.data.*
import io.circe.*, io.circe.parser.*

object ZipImport:
  def importBinary(b: BinaryData): IO[List[ParsedSource]] = for
    tmpFile <- createTmpFile
    _       <- writeData(b, tmpFile)
    result  <- insertData(tmpFile).attempt
    _       <- rmTmpFile(tmpFile)
    result  <- result match
                 case Left(t)  => IO.raiseError[List[ParsedSource]](t)
                 case Right(e) => e.pure[IO]
  yield result

  def createTmpFile: IO[Path]                     = IO { Files.createTempFile("monodi", "import") }
  def writeData(b: BinaryData, p: Path): IO[Unit] = IO { Files.write(p, b.content); () }
  def insertData(p: Path): IO[List[ParsedSource]] = for
    zipFile <- IO { new ZipFile(p.toFile) }
    parsed  <- IO {
                 for
                   gathered <-
                     zipFile.entries.asScala.toList
                       .traverse(parseEntry)
                       .toEither // .map(_.map(_.mapValues(UnparsedSource.toValid)).combineAll).sequence.toEither
                   unique   <- gathered
                                 .map(_.view.mapValues((ups: UnparsedSource) => UnparsedSource.toValid(ups)).toMap)
                                 .combineAll
                                 .sequence
                                 .toEither
                   parsed   <- parseData(zipFile)(unique).toEither
                 yield parsed
               }.attempt
    _       <- IO { zipFile.close }
    result  <- parsed match
                 case Left(t)         => IO.raiseError(t)
                 case Right(Left(t))  => IO.raiseError(new Throwable(t.toList.mkString("\n")))
                 case Right(Right(e)) => e.pure[IO]
  yield result

  def rmTmpFile(p: Path): IO[Unit] = IO { Files.delete(p) }

  def parseData(zf: ZipFile)(m: Map[String, UnparsedSource]): ValidatedNel[String, List[ParsedSource]] =

    def parseDocument(sId: String)(entry: UnparsedDocument): ValidatedNel[String, ParsedDocument] = (
      entry.id.pure[ValidatedNel[String, _]],
      entry.meta.traverse(m =>
        decode[Document](readEntry(zf, m))
          .leftMap(s"Could not parse meta in ${sId}/${entry.id}: " + _.getMessage)
          .toValidatedNel,
      ),
      entry.data.traverse(d =>
        decode[Json](readEntry(zf, d))
          .leftMap(s"Could not parse data in ${sId}/${entry.id}: " + _.getMessage)
          .toValidatedNel,
      ),
      entry.writers.traverse(s =>
        decode[List[User]](readEntry(zf, s))
          .leftMap(s"Could not parse writers in ${sId}/${entry.id}: " + _.getMessage)
          .toValidatedNel,
      ),
    ).mapN(ParsedDocument.apply)

    def parseSource(entry: UnparsedSource): ValidatedNel[String, ParsedSource] = (
      entry.id.pure[ValidatedNel[String, _]],
      entry.meta.traverse(m =>
        decode[Source](readEntry(zf, m)).leftMap(s"Could not parse meta in ${entry.id}: " + _.getMessage).toValidatedNel,
      ),
      entry.documents.values.toList.traverse(parseDocument(entry.id)),
    ).mapN(ParsedSource.apply)

    m.values.toList.traverse(parseSource)

  def parseEntry(z: ZipEntry): ValidatedNel[String, Map[String, UnparsedSource]] =
    //@formatter:off
    z.getName match
      case sourceMetaPath(sId)           => Valid(Map(decodeId(sId) -> UnparsedSource(decodeId(sId), Some(z), Map())))
      case documentMetaPath(sId, dId)    => Valid(Map(decodeId(sId) -> UnparsedSource(decodeId(sId), None,    Map(decodeId(dId) -> UnparsedDocument(decodeId(dId), Some(z), None, None)))))
      case documentDataPath(sId, dId)    => Valid(Map(decodeId(sId) -> UnparsedSource(decodeId(sId), None,    Map(decodeId(dId) -> UnparsedDocument(decodeId(dId), None, Some(z), None)))))
      case documentWritersPath(sId, dId) => Valid(Map(decodeId(sId) -> UnparsedSource(decodeId(sId), None,    Map(decodeId(dId) -> UnparsedDocument(decodeId(dId), None, None, Some(z))))))
      case _ if z.isDirectory            => Valid(Map())
      case _                             => Invalid(s"Unkown zip entry ${z.getName}").toValidatedNel
    //@formatter:on

  def decodeId(s: String): String = s
    .replace("$", "/")
    .replace("#slash", "/")

  def encodeId(s: String): String =
    s.replace("/", "#slash")

  private val sourceMetaPath      = raw"((?u)[^/]+)/meta.json".r
  private val documentMetaPath    = raw"((?u)[^/]+)/([^/]+)/meta.json".r
  private val documentDataPath    = raw"((?u)[^/]+)/([^/]+)/data.json".r
  private val documentWritersPath = raw"((?u)[^/]+)/([^/]+)/writers.json".r

  def traverseMap[K, V, VV, F[_]](m: Map[K, V])(f: (K, V) => F[VV])(using ap: Applicative[F]): F[Map[K, VV]] =
    m.toList.traverse({ case (k, v) => f(k, v).map(vv => k -> vv) }).map(_.toMap)

  def readEntry(zf: ZipFile, ze: ZipEntry): String =
    val s = zf.getInputStream(ze)
    try
      val scanner = new Scanner(s, "utf-8").useDelimiter("\\Z")
      if scanner.hasNext then scanner.next()
      else ""
    finally
      s.close()
