package de.olyro.monodi
package exprt

import de.olyro.monodi.db.DBRunner
import zio.*

import java.nio.file.*
import zio.ZIOAppDefault

object ExportLibrary extends ZIOAppDefault:
  val svg                                  = Svg(
    new CanvasConfig(embedFonts = false, useSystemFontNames = false, width = 1280.0, height = 1280.0 * Math.sqrt(2)),
  )
  val path                                 = Paths.get("/tmp/monodi")
  override def run: ZIO[Any, Nothing, Int] =
    def exportSingle(uuid: String) = for
      (notes, _) <- db.DBRunner.runZ(db.DBRunner.getDocumentNotesDecoded(uuid)).someOrFailException.orDie
      _          <- ZIO
                      .attemptBlocking(Files.writeString(path `resolve` s"$uuid.svg", svg.makeSVG(svg `drawAll` notes, "")))
                      .orDie
                      .when(notes.children.nonEmpty)
    yield 0

    (for
      ids <- db.DBRunner.runZ(db.DBRunner.getAllDocumentIdsWithTheirDokumentIdField).map(_.map(_._1)).orDie
      _   <- zio.stream.ZStream.fromIterable(ids).mapZIOPar(Export.availableProcessors)(exportSingle).runDrain
      _   <- ZIO.attemptBlocking {
               Util.unsafeCopy(this.getClass, TextBox.normalFontPath, path)
               Util.unsafeCopy(this.getClass, TextBox.smcpFontPath, path)
             }.orDie
    yield 0).provideSomeLayer(DBRunner.live.orDie)
