package de.olyro.monodi
package exprt

import de.olyro.monodi.data.notes.Container
import de.olyro.monodi.db.DBRunner
import io.circe.syntax.*
import zio.*
import zio.stream.ZStream

import java.nio.file.*
import de.olyro.monodi.exprt.print.Print
import zio.{Console, ZIOAppDefault}

object ExportSingleSvg extends ZIOAppDefault:
  // private val aa13_107v_7    = "8840bf6b-cf61-4e16-aa43-800b50c9a21a"
  // private val aa13_108_9     = "d8143e0e-42be-4601-afa2-d1b06708875a"
  // private val aa13_109_1     = "8f504abe-625d-45a0-b545-05e130543fff"
  // private val aa13_110_3     = "a8470004-eb1b-4215-b9b5-5f2afefaa964"
  // private val aa13_110v_1    = "2e71ce4e-c196-4bbc-b9c5-80b5c14e262d"
  // private val ass695_92v_6   = "ef682afc-bfd0-4762-b5a7-18e241f6102d"
  // private val ma289_127v_3   = "939211bf-5491-453c-81f5-a91fdfbb3aee"
  // private val ma289_135_9    = "97420e96-3c63-4409-8d2a-fe08511cab8f"
  // private val or201_224_6    = "42053e20-74fa-4992-b02c-e993cdb6a23f"
  // private val pa778__201     = "ef02eb99-64d1-4755-a164-659ddce11394"
  // private val pa1235_213v_11 = "780fdd13-e413-4aa2-8c38-eafbc82e2076"
  // private val pa14452_186_6  = "da4012d3-faf0-444d-b368-d5e375ec6d44"
  // private val pa14452_196_12 = "da76230f-f70a-4a71-9377-308589153435"
  // private val paA135_145v_a1 = "5f39cd6c-745f-4e0a-8890-c5ba3f6adb03"
  // private val paA135_232v_a4 = "543d6c1c-0198-434e-81fc-9948970df789"
  // private val rou382_1_9_33  = "4caa51d6-6d88-4019-9f17-885c6e2eceb5"
  // private val sol596_59_19   = "c19ecc43-1ef3-437d-8753-2c5c07b3a01c"
  // private val ver10_155v_5   = "0bd64128-daeb-495b-a112-992418b6fb82"
  // private val int5_96v_1     = "cce4455f-203b-44b6-be20-19266b9c0ea4"
  private val test_long_line = "0a03b11e-85ad-4c90-b574-f00cb59241ec"

  override def run: ZIO[Any, Throwable, Int] = (for
    notes        <- db.DBRunner
                      .runZ(db.DBRunner.getDocumentNotesDecoded(test_long_line))
                      .someOrFailException
                      .orDie
    svg           =
      Svg(
        new CanvasConfig(
          embedFonts = false,
          useSystemFontNames = false,
          width = 1280.0,
          height = 1280.0 * Math.sqrt(2),
          drawCommentMarks = true
        )
      )
    _            <- Console.printLine(notes._2.toString)
    _            <- ZIO.attemptBlocking(Files.writeString(Paths.get("/tmp/cmpl.svg"), svg.makeSVG(svg.drawAll(notes._1), ""))).orDie
    _            <- ZIO.attemptBlocking(Files.writeString(Paths.get("/tmp/cmpl.json"), (notes._1: Container).asJson.spaces2)).orDie
    withFontNames = svg.useSystemFontNames
    settings      = print.Settings(pageNumberPrefix = None)
    printPages   <- Print(withFontNames, settings).drawPrintView(notes._1).map(_.map(withFontNames.makeSVG(_, "")))
    _            <- ZIO.foreach(printPages.zipWithIndex) { case (pp, i) =>
                      ZIO.attemptBlocking(Files.writeString(Paths.get(s"/tmp/ppage$i.svg"), pp)).orDie
                    }
    _            <- Pdf.default
                      .printSvg(printPages)
                      .flatMap(pdf => ZIO.attemptBlocking(Files.write(Paths.get("/tmp/cmpl.pdf"), pdf.data)))
                      .orDie
    (errs, pages) = Pages.mkPages(notes._1, "???", 0).partitionMap(identity)
    _            <- ZStream.fromIterable(errs).mapZIO(x => Console.printLine(x)).runDrain
    _            <- ZStream
                      .fromIterable(svg.drawPages(pages.map(_.notes)))
                      .zipWithIndex
                      .mapZIO { case (bb, idx) =>
                        val drawn = svg.makeSVG(bb, "")
                        ZIO.attemptBlocking(Files.writeString(Paths.get("/tmp", s"page_$idx.svg"), drawn)).orDie
                      }
                      .runDrain
  yield 0).provide(DBRunner.live.orDie, ContainerEngine.live.orDie)
