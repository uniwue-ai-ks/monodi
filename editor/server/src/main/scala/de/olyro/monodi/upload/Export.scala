package de.olyro.monodi
package upload

import data.*
import cats.implicits.*
import cats.effect.*
import io.circe.*, io.circe.syntax.*
import java.nio.file.*

object Export:

  def `exprt`: IO[Unit] =
    db.DBRunner.run(db.DBRunner.listSources).flatMap(_.traverse_(ioExportSource)) *>
      db.DBRunner.run(db.DBRunner.listDocuments).flatMap(_.traverse_(exportDocument))

  def exportDocument(d: Document): IO[Unit] =
    db.DBRunner.run(db.DBRunner.getDocumentNotes(d.id)).flatMap(notes => ioExportDocument(d, notes.get))

  def ioExportSource(s: Source): IO[Unit] =
    val sroot = Paths.get(s"/tmp/exprt/${s.id}")
    for
      _ <- IO { Files.createDirectories(sroot) }
      _ <- IO { Files.write(sroot.resolve("meta.json"), s.asJson.spaces2.getBytes("UTF-8")) }
    yield ()

  def ioExportDocument(d: Document, notes: Json): IO[Unit] =
    val sroot = Paths.get(s"/tmp/exprt/${d.quelle_id}/${d.id}/")
    for
      _ <- IO { Files.createDirectories(sroot) }
      _ <- IO { Files.write(sroot.resolve("meta.json"), d.asJson.spaces2.getBytes("UTF-8")) }
      _ <- IO { Files.write(sroot.resolve("data.json"), notes.asJson.spaces2.getBytes("UTF-8")) }
    yield ()
