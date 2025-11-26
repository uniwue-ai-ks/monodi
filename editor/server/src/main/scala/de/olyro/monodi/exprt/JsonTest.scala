package de.olyro.monodi
package exprt

import zio.{Ref as _, *}

import java.nio.file.*
import de.olyro.monodi.data.notes.*
import de.olyro.monodi.data.notes.Container
import de.olyro.monodi.db.DBRunner
import de.olyro.monodi.db.DBRunner.DBDoobie

import java.nio.charset.StandardCharsets
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import zio.ZIOAppDefault
import zio.Console.{print, printLine}

object JsonTest extends ZIOAppDefault:
  def run: ZIO[Any, Nothing, Int] =
    (for
      ids <- db.DBRunner.runZ(db.DBRunner.listDocumentIds).orDie
      _   <- ZIO.foreach(ids.zipWithIndex)((checkId).tupled)
    yield 0).provide(DBRunner.live.orDie)

  def checkId(id: String, n: Int): URIO[DBDoobie, Unit] =
    for
      notes <- db.DBRunner.runZ(db.DBRunner.getDocumentNotes(id)).someOrFailException.orDie
      in     = Evolutions.applyAllJsonEvolutions(notes) match
                 case Left(s)  => throw new RuntimeException(s)
                 case Right(r) => r._1
      a      = (in: Container).asJson.printWith(p)
      b      = decode[Container]((in: Container).asJson.noSpaces).getOrElse(throw new RuntimeException()).asJson.printWith(p)
      _     <- if a == b then (if n % 100 == 0 then (printLine) else (print)) (".").orDie
               else
                 ZIO.succeed(
                   Files.write(
                     Paths.get("/tmp/json_" + id + "_old"),
                     a.getBytes(StandardCharsets.UTF_8),
                   ),
                 ) *>
                   ZIO.succeed(
                     Files
                       .write(
                         Paths.get("/tmp/json_" + id + "_new"),
                         b.getBytes(StandardCharsets.UTF_8),
                       ),
                   ) *>
                   printLine(s"failed with doc $id").orDie
    yield ()

  val p = Printer.spaces2SortKeys.copy(dropNullValues = true)
