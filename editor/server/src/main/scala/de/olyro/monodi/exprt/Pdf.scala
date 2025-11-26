package de.olyro.monodi
package `exprt`

import zio.*
import zio.ZIO.attemptBlocking
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.UUID

class Pdf(cfg: Pdf.Config):
  import Pdf.*

  val cmd = Seq(
    "rsvg-convert",
    "--unlimited",
    "--format=pdf",
    s"--page-width=${cfg.paperWidth}mm",
    s"--page-height=${cfg.paperHeight}mm",
    "--keep-aspect-ratio",
    s"--width=${cfg.paperWidth - cfg.marginLeft - cfg.marginRight}mm",
    s"--top=${cfg.marginTop}mm",
    s"--left=${cfg.marginLeft}mm"
  )

  private def externalSvg2Pdf(pages: List[String]): ZIO[ContainerEngine, Throwable, PdfDoc] = ZIO.scoped:
    val inContainerRoot = Paths.get("/tmp")

    for
      tmpDir          <- createTempDirAndFiles(pages)
      inContainerPages = tmpDir.relativePaths.map(path => inContainerRoot.resolve(path).toString)
      bytes           <- ZIO.serviceWithZIO[ContainerEngine]:
                           _.run(
                             name = UUID.randomUUID().toString,
                             mounts = List((tmpDir.dir.toString, inContainerRoot.toString)),
                             command = cmd ++ inContainerPages
                           )
    yield PdfDoc(bytes)

  final private case class TmpDir(dir: Path, paths: List[Path]):
    lazy val relativePaths = paths.map(path => dir.relativize(path))

  private def createTempDirAndFiles(pages: List[String]): ZIO[Scope, Throwable, TmpDir] =
    for
      dir   <- Util.createCleanTempDir("monodi-pdf")
      paths <- ZIO.foreach(pages.zipWithIndex): (page, idx) =>
                 val path = dir.resolve(f"monodi-pdf-p$idx%05d.svg")
                 for _ <- attemptBlocking(Files.writeString(path, page, StandardCharsets.UTF_8))
                 yield path
    yield TmpDir(dir, paths.toList)

  def printSvg(pages: List[String]): ZIO[ContainerEngine, Throwable, PdfDoc] = externalSvg2Pdf(pages)

object Pdf:
  case class PdfDoc(data: Array[Byte])
  case class Config(
      marginTop: Int,
      marginLeft: Int,
      marginRight: Int,
      paperWidth: Double,
      paperHeight: Double
  )

  val defaultConfig = Config(14, 10, 10, 210, 297)
  val default       = new Pdf(defaultConfig)
