package de.olyro.monodi
package exprt

import de.olyro.monodi.Util.{Fence, JsonArrayWriter}
import de.olyro.monodi.data.notes.RootContainer
import de.olyro.monodi.data.{Document, Source}
import de.olyro.monodi.db.DBRunner
import de.olyro.monodi.db.DBRunner.DBDoobie
import de.olyro.monodi.exprt.Message.FromDoc
import io.circe.Codec
import zio.*

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object CantusExport:
  private val dbCode = "CM"

  final case class CantusRecord(
      siglum: String,
      srclink: String,
      chantlink: String,
      folio: String,
      incipit: String,
      cantus_id: String,
      db: String,
      melody_id: Option[String],
      genre: Option[String],
      century: Option[String],
      full_text: Option[String]
  ) derives io.circe.Codec.AsObject

  def run(
      config: ExportConfig
  ): ZIO[DBDoobie & Fence, Nothing, List[Message]] =
    (config.cantusJsonPath, config.publicViewBaseUrl) match
      case (None, _)                => ZIO.succeed(Nil)
      case (Some(_), None)          =>
        ZIO.succeed(
          List(
            Message(
              Message.Warning,
              Message.NoCustomDir,
              "cantusJsonPath is set but publicViewBaseUrl is missing; skipping Cantus export"
            )
          )
        )
      case (Some(path), Some(base)) =>
        Fence
          .measure("cantus-export")(
            ZIO.scoped(
              for
                _       <- zio.Console.printLine("Exporting Cantus records...").orDie
                docs    <- DBRunner.runZ(DBRunner.listCantusIndexedDocuments).orDie
                _       <- zio.Console.printLine(s"Processing ${docs.length} documents...").orDie
                sources <- DBRunner.runZ(DBRunner.listSources).map(_.map(s => s.id -> s).toMap).orDie
                writer  <- JsonArrayWriter.make(path).orDie
                msgs    <- ZIO.foldLeft(docs)(List.empty[Message]) { (acc, doc) =>
                             processDocument(doc, sources, base, writer).map(_ ::: acc)
                           }
              yield msgs.reverse
            )
          )

  private def processDocument(
      doc: Document,
      sources: Map[String, Source],
      base: String,
      writer: JsonArrayWriter
  ): ZIO[DBDoobie, Nothing, List[Message]] =
    sources.get(doc.quelle_id) match
      case None      =>
        ZIO.succeed(
          List(Message(Message.Warning, FromDoc(doc), s"source ${doc.quelle_id} not found; skipped"))
        )
      case Some(src) =>
        for
          _       <- zio.Console.printLine(s"Processing document ${doc.id}...").orDie
          decoded <- DBRunner.runZ(DBRunner.getDocumentNotesDecoded(doc.id)).orDie
          notes    = decoded.map(_._1).toList
          result  <- buildRecord(doc, src, notes, base) match
                       case Left(msg)     => ZIO.succeed(List(msg))
                       case Right(record) => writer.write(record).orDie.as(List.empty[Message])
        yield result

  private def buildRecord(
      doc: Document,
      src: Source,
      notes: List[RootContainer],
      base: String
  ): Either[Message, CantusRecord] =
    val cantusId = doc.additionalData.getOrElse("Cantus_ID", "").trim
    val siglum   = src.cantus_siglum.trim
    val folio    = doc.foliostart.trim
    val incipit  = doc.textinitium.trim

    val missing: List[String] = List(
      Option.when(siglum.isEmpty)("Cantus_Siglum on source"),
      Option.when(folio.isEmpty)("foliostart"),
      Option.when(incipit.isEmpty)("textinitium")
    ).flatten

    if missing.nonEmpty then
      Left(
        Message(
          Message.Warning,
          FromDoc(doc),
          s"skipped Cantus record (cantus_id=$cantusId): missing ${missing.mkString(", ")}"
        )
      )
    else
      val melodyId = doc.additionalData.getOrElse("Cantus_Melody_ID", "").trim
      val genre    = doc.additionalData.getOrElse("Cantus_Genre", "").trim
      val century  = src.cantus_century.trim
      val fullText = DocCreator.extractFullText(notes)

      Right(
        CantusRecord(
          siglum = siglum,
          srclink = s"$base/q/${URLEncoder.encode(src.id, StandardCharsets.UTF_8)}",
          chantlink = s"$base/d/${URLEncoder.encode(doc.id, StandardCharsets.UTF_8)}",
          folio = folio,
          incipit = incipit,
          cantus_id = cantusId,
          db = dbCode,
          melody_id = Option(melodyId).filter(_.nonEmpty),
          genre = Option(genre).filter(_.nonEmpty),
          century = Option(century).filter(_.nonEmpty),
          full_text = Option(fullText).filter(_.nonEmpty)
        )
      )
