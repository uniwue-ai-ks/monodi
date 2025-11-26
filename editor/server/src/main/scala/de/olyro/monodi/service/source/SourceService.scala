package de.olyro.monodi
package service
package source

import data.*
import db.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import Result.{Ok as _, *}
import de.olyro.monodi.upload.Upload

object SourceService:
  val list = AuthRoutes.ofUser({
    case POST -> Root / "api" / "source" / "list" => _ => for
      sources  <- DBRunner.run(DBRunner.listSources)
      result <- Ok(SourcesRetrieved(sources))
    yield result
  })

  val get = AuthRoutes.ofUser({
    case req@POST -> Root / "api" / "source" / "get" => _ => for
      id      <- req.as[String]
      source  <- DBRunner.run(DBRunner.getSource(id))
      result  <- source match {
        case None    => Ok(SourceNotFound)
        case Some(s) => Ok(SourceRetrieved(s))
      }
    yield result
  })

  val getSigle = AuthRoutes.ofUser({
    case req@POST -> Root / "api" / "source" / "getSigle" => _ => for
      id      <- req.as[String]
      source  <- DBRunner.run(DBRunner.getSource(id))
      result  <- source match {
        case None    => Ok(SourceNotFound)
        case Some(s) => Ok(SigleRetrieved(s.quellensigle))
      }
    yield result
  })

  val query = AuthRoutes.ofUser({
    case req@POST -> Root / "api" / "source" / "query" => _ => for
      query   <- req.as[SourceQuery]
      sources <- DBRunner.run(DBRunner.listSources)
      result  <- Ok(SourcesRetrieved(sources.filter(matches(query))))
    yield result
  })

  val importZip = AuthRoutes.ofRole(Role.Admin)({
    case req@POST -> Root / "api" / "source" / "import" => _ => for
      data    <- req.as[BinaryData]
      errors  <- Upload.uploadZip(data)
      result  <- Ok(UploadFinished(errors))
    yield result
  })

  val importDocumentExcel = AuthRoutes.ofRole(Role.Admin)({
    case req@POST -> Root / "api" / "source" / "importDocuments" => _ => for
      data    <- req.as[BinaryData]
      errors  <- Upload.uploadDocumentsTable(data)
      result  <- Ok(UploadFinished(errors))
    yield result
  })

  val importSourceExcel = AuthRoutes.ofRole(Role.Admin)({
    case req@POST -> Root / "api" / "source" / "importSources" => _ => for
      data    <- req.as[BinaryData]
      errors  <- Upload.uploadSourceTable(data)
      result  <- Ok(UploadFinished(errors))
    yield result
  })

  val deleteDocumentExcel = AuthRoutes.ofRole(Role.Admin)({
    case req@POST -> Root / "api" / "source" / "deleteDocuments" => _ => for
      data    <- req.as[BinaryData]
      _       <- Upload.deleteDocuments(data)
      result  <- Ok(UploadFinished(Nil))
    yield result
  })

  val deleteSourceExcel = AuthRoutes.ofRole(Role.Admin)({
    case req@POST -> Root / "api" / "source" / "deleteSources" => _ => for
      data    <- req.as[BinaryData]
      _       <- Upload.deleteSources(data)
      result  <- Ok(UploadFinished(Nil))
    yield result
  })



  private def matches(q: SourceQuery)(s: Source): Boolean =
    containsIfThere(s.quellensigle,         q.quellensigle) &&         
    containsIfThere(s.herkunftsregion,      q.herkunftsregion) &&      
    containsIfThere(s.herkunftsort,         q.herkunftsort) &&         
    containsIfThere(s.herkunftsinstitution, q.herkunftsinstitution) && 
    containsIfThere(s.ordenstradition,      q.ordenstradition) &&      
    containsIfThere(s.quellentyp,           q.quellentyp) &&           
    containsIfThere(s.bibliotheksort,       q.bibliotheksort) &&       
    containsIfThere(s.bibliothek,           q.bibliothek) &&           
    containsIfThere(s.bibliothekssignatur,  q.bibliothekssignatur) &&  
    containsIfThere(s.kommentar,            q.kommentar) &&
    containsIfThere(s.datierung,            q.datierung)

  private def containsIfThere(s: String, o: Option[String]): Boolean = o match
    case None        => true
    case Some(inner) => s.toLowerCase().contains(inner.toLowerCase.trim())


