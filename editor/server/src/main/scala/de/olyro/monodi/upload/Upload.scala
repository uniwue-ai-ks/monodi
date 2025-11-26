package de.olyro.monodi
package upload

import data.*
import cats.implicits.*
import cats.effect.*
import doobie.*, doobie.implicits.*
import de.olyro.monodi.upload.zip.ZipImport
import de.olyro.monodi.upload.excel.*
import java.util.UUID
import io.circe.*, io.circe.parser.*
import java.io.ByteArrayInputStream
import cats.effect.std.UUIDGen

object Upload:
  def uploadZip(b: BinaryData): IO[List[String]] =
    ZipImport
      .importBinary(b)
      .flatMap(data => db.DBRunner.run(insert(data)))
      .attempt
      .map({
        case Left(t)  => List(t.getMessage())
        case Right(_) => Nil
      })

  def uploadDocumentsTable(b: BinaryData): IO[List[String]] =
    uploadExcel(b, excel.document.TableParser.parse)

  def uploadSourceTable(b: BinaryData): IO[List[String]] =
    uploadExcel(b, excel.source.TableParser.parse)

  def deleteDocuments(b: BinaryData): IO[Unit] =
    excel.ExcelParser.parseTable(new ByteArrayInputStream(b.content)) match
      case Left(message) => IO.raiseError[Unit](new RuntimeException(message))
      case Right(rows)   => db.DBRunner.run(rows.flatMap(_.headOption).toList.traverse_(db.DBRunner.removeDocument))

  def deleteSources(b: BinaryData): IO[Unit] =
    excel.ExcelParser.parseTable(new ByteArrayInputStream(b.content)) match
      case Left(message) => IO.raiseError[Unit](new RuntimeException(message))
      case Right(rows)   => db.DBRunner.run(rows.flatMap(_.headOption).toList.traverse_(db.DBRunner.removeSource))

  private def uploadExcel(
      b: BinaryData,
      parse: Vector[Vector[String]] => Either[String, (List[RowError], List[ParsedSource])],
  ): IO[List[String]] =
    ExcelParser
      .parseTable(b.content)
      .flatMap(parse) match
      case Left(error)              => List(error).pure[IO]
      case Right((errors, sources)) =>
        db.DBRunner
          .run(insert(sources))
          .attempt
          .map({
            case Left(t)  => Util.stringify(t) :: errors.map(_.show)
            case Right(_) => errors.map(_.show)
          })

  private def insert(data: List[ParsedSource]): ConnectionIO[Unit] =
    def insertSource(e: ParsedSource): ConnectionIO[Unit] =
      e.meta.traverse_(m => db.DBRunner.importSource(m.copy(id = e.id))) *> e.documents.traverse_(insertDocument)

    def insertDocument(d: ParsedDocument): ConnectionIO[Unit] = ((d.meta, d.data) match {
      case (Some(meta), Some(data)) => db.DBRunner.importDocument(CreateDocument(meta.copy(id = d.id), data))
      case (Some(meta), None)       =>
        getNotesOrCreate(d.id).flatMap(ns => db.DBRunner.importDocument(CreateDocument(meta.copy(id = d.id), ns)))
      case (None, Some(data))       => db.DBRunner.saveDocumentNotes(SaveNotes(d.id, data)).void
      case (None, None)             => ().pure[ConnectionIO]
    }) *> d.writers.traverse_(ws =>
      db.DbPermissions.clearDocumentPermissions(d.id) *> ws.traverse_(db.DbPermissions.giveWriteAccess(d.id)),
    )

    data.traverse_(insertSource)

  private def getNotesOrCreate(id: String): ConnectionIO[Json] =
    db.DBRunner
      .getDocumentNotes(id)
      .flatMap({
        case None    => randomUUID.map(generateNotes)
        case Some(n) => n.pure[ConnectionIO]
      })

  private def generateNotes(uuid: UUID): Json =
    decode[Json](
      raw"""{"comments":[],"uuid":"XXX","kind":"RootContainer","children":[],"documentType":"Level1"}""".replace(
        "XXX",
        uuid.toString,
      ),
    ).getOrElse(throw new RuntimeException("should never happen"))

  private val randomUUID: ConnectionIO[UUID] = UUIDGen[ConnectionIO].randomUUID

