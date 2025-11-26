package de.olyro.monodi
package exprt

import cats.implicits.toShow
import de.olyro.monodi.exprt.ExportConfig.ExportSetConfig
import de.olyro.monodi.exprt.Turtle.Raw
import de.olyro.monodi.Util.CirceNioInstances.given
import io.circe.parser.parse
import io.circe.{Decoder, Encoder, Json}
import zio.*

import java.io.IOException
import java.nio.file.*
import zio.ZIO.attemptBlockingIO

final case class ExportConfig(
    svgPath: Path,
    logPath: Path,
    mafftPath: Path,
    editorPseudonyms: Map[String, String],
    editorNames: Map[String, String],
    exportSets: List[ExportSetConfig]
) derives io.circe.Codec.AsObject

object ExportConfig:
  final case class ExportSetConfig(
      ttlPath: Path,
      jsonPath: Option[Path],
      statusWhiteList: List[String],
      documentNoteStatusWhiteList: List[String],
      documentApparatusStatusWhiteList: List[String],
      extraCss: String,
      turtleDefinitionLineReplacements: List[ExportConfig.TurtleDefinitionsLineReplacer]
  ) derives io.circe.Codec.AsObject

  final case class TurtleDefinitionsLineReplacer(regexToFind: String, replacementLine: String)

  object TurtleDefinitionsLineReplacer:
    given Encoder[TurtleDefinitionsLineReplacer] =
      Encoder.instance(r => Json.arr(Json.fromString(r.regexToFind), Json.fromString(r.replacementLine)))
    given Decoder[TurtleDefinitionsLineReplacer] = Decoder
      .decodeArray[String]
      .emap:
        case Array(regex, replacer) => Right(TurtleDefinitionsLineReplacer(regex, replacer))
        case x                      => Left(s"invalid definition replacer: ${x.mkString("[", ", ", "]")}")

    def applyAll(replacers: List[TurtleDefinitionsLineReplacer])(raw: String) =
      Raw(replacers.foldLeft(raw)((data, $) => data.replaceAll($.regexToFind, $.replacementLine)))

  def load(paths: String*): Task[ExportConfig] =
    val getFile = paths
      .map(p => attemptBlockingIO(Path.of(p)).filterOrFail(Files.isRegularFile(_))(new NoSuchFileException(p)))
      .reduce(_ `orElse` _)
      .mapError(_ => new IOException(s"Could not find config json in ${paths.mkString("[", ", ", "]")}"))

    for
      file <- getFile
      str  <- attemptBlockingIO { Files.readString(file) }
      conf <- ZIO.fromEither(parse(str).flatMap(_.as[ExportConfig])).tapError(e => ZIO.succeed(println(e.show)))
    yield conf

  val load: Task[ExportConfig] =
    load("/var/lib/monodi/editor/config.json", "config.json", "../config.json", "../../config.json")
