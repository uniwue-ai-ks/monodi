package de.olyro.monodi
package service
package document

import cats.effect.*
import data.*
import db.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import Result.{Ok as ROk, *}
import de.olyro.monodi.data.notes.Container
import io.circe.syntax.*

object DocumentService:
  val list = AuthRoutes.ofUser({ case POST -> Root / "api" / "document" / "list" =>
    _ =>
      for
        documents <- DBRunner.run(DBRunner.listDocuments)
        result    <- Ok(DocumentsRetrieved(documents))
      yield result
  })

  val update = AuthRoutes.ofUser({ case req @ POST -> Root / "api" / "document" / "update" =>
    u =>
      for
        update   <- req.as[CreateDocument]
        canWrite <- DBRunner.run(DbPermissions.mayWriteDocument(u, update.document.id))
        result   <- if !canWrite then Ok(InsufficientPermissions)
                    else
                      for
                        wasEdited <- DBRunner.run(
                                       DBRunner.saveDocumentNotes(SaveNotes(update.document.id, update.notes)),
                                     )
                        result    <- if wasEdited then Ok(ROk) else Ok(DocumentNotFound)
                      yield result
      yield result
  })

  val get = AuthRoutes.ofUser({ case req @ POST -> Root / "api" / "document" / "get" =>
    _ =>
      for
        id       <- req.as[String]
        document <- DBRunner.run(DBRunner.getDocument(id))
        result   <- document match {
                      case None    => Ok(DocumentNotFound)
                      case Some(d) => Ok(DocumentRetrieved(d))
                    }
      yield result
  })

  val query = AuthRoutes.ofUser({ case req @ POST -> Root / "api" / "document" / "query" =>
    _ =>
      for
        query     <- req.as[DocumentQuery]
        documents <- DBRunner.run(DBRunner.listDocuments)
        result    <- Ok(DocumentsRetrieved(documents.filter(matches(query))))
      yield result
  })

  val getNotes = AuthRoutes.ofUser({ case req @ POST -> Root / "api" / "document" / "getNotes" =>
    _ =>
      for
        id       <- req.as[String]
        document <- DBRunner.run(DBRunner.getDocumentNotes(id))
        result   <- document match {
                      case None    => Ok(DocumentNotFound)
                      case Some(d) => Ok(NotesRetrieved(d))
                    }
      yield result
  })

  val verifyNotes = AuthRoutes.ofUser({ case req @ POST -> Root / "api" / "document" / "verifyNotes" =>
    _ =>
      for
        in     <- req.as[String]
        decoded = Container.parse(in)
        result <- decoded match {
                    case Left(t)  => IO(println(t.toString)) *> Ok(Failed)
                    case Right(d) => Ok(NotesRetrieved((d._1: Container).asJson))
                  }
      yield result
  })

  val saveNotes = AuthRoutes.ofUser({ case req @ POST -> Root / "api" / "document" / "saveNotes" =>
    u =>
      for
        saveNotes <- req.as[SaveNotes]
        canWrite  <- DBRunner.run(DbPermissions.mayWriteDocument(u, saveNotes.id))
        result    <- if canWrite then DBRunner.run(DBRunner.saveDocumentNotes(saveNotes)) *> Ok(ROk)
                     else Ok(InsufficientPermissions)
      yield result
  })

  private def matches(q: DocumentQuery)(d: Document): Boolean =
    containsIfThere(d.dokumenten_id, q.dokumenten_id) &&
      containsIfThere(d.gattung1, q.gattung1) &&
      containsIfThere(d.gattung2, q.gattung2) &&
      containsIfThere(d.festtag, q.festtag) &&
      containsIfThere(d.feier, q.feier) &&
      containsIfThere(d.textinitium, q.textinitium) &&
      containsIfThere(d.bibliographischerverweis, q.bibliographischerverweis) &&
      containsIfThere(d.druckausgabe, q.druckausgabe) &&
      containsIfThere(d.zeilenstart, q.zeilenstart) &&
      containsIfThere(d.foliostart, q.foliostart) &&
      containsIfThere(d.kommentar, q.kommentar)

  private def containsIfThere(s: String, o: Option[String]): Boolean =
    o match
      case None        => true
      case Some(inner) => s.toLowerCase().contains(inner.toLowerCase.trim())

