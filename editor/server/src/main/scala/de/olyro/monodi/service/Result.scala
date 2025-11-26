package de.olyro.monodi
package service

import data.*
import io.circe.*

object Result:
  case object Ok
  given Encoder[Ok.type] =
    Encoder.instance(_ => Json.obj(("kind", Json.fromString("Ok"))))

  case object Failed
  given Encoder[Failed.type] =
    Encoder.instance(_ => Json.obj(("kind", Json.fromString("Failed"))))

  case object LoginRequired
  given Encoder[LoginRequired.type] =
    Encoder.instance(_ => Json.obj(("kind", Json.fromString("LoginRequired"))))

  case object InsufficientPermissions
  given Encoder[InsufficientPermissions.type] =
    Encoder.instance(_ => Json.obj(("kind", Json.fromString("InsufficientPermissions"))))

  case object InvalidUsernameFormat
  given Encoder[InvalidUsernameFormat.type] =
    Encoder.instance(_ => Json.obj(("kind", Json.fromString("InvalidUsernameFormat"))))

  case object LoginFailed
  given Encoder[LoginFailed.type] =
    Encoder.instance(_ => Json.obj(("kind", Json.fromString("LoginFailed"))))

  final case class LoginSuccessful(
      user: User,
      roles: List[Role],
      token: String,
  )
  given Encoder[LoginSuccessful] =
    Encoder.forProduct4("kind", "user", "roles", "token")(ls => ("LoginSuccessful", ls.user, ls.roles, ls.token))

  case object UserAlreadyExists
  given Encoder[UserAlreadyExists.type] =
    Encoder.instance(_ => Json.obj(("kind", Json.fromString("UserAlreadyExists"))))

  final case class UserInfosRetrieved(
      infos: List[UserInfo],
  )
  given Encoder[UserInfosRetrieved] =
    Encoder.forProduct2("kind", "infos")(ui => ("UserInfosRetrieved", ui.infos))

  case object TriedToRemoveSelf
  given Encoder[TriedToRemoveSelf.type] =
    Encoder.instance(_ => Json.obj(("kind", Json.fromString("TriedToRemoveSelf"))))

  final case class SourcesRetrieved(
      sources: List[Source],
  )
  given Encoder[SourcesRetrieved] =
    Encoder.forProduct2("kind", "sources")(si => ("SourcesRetrieved", si.sources))

  case object SourceNotFound
  given Encoder[SourceNotFound.type] =
    Encoder.instance(_ => Json.obj(("kind", Json.fromString("SourceNotFound"))))

  final case class SigleRetrieved(
      sigle: String,
  )
  given Encoder[SigleRetrieved] =
    Encoder.forProduct2("kind", "sigle")(si => ("SigleRetrieved", si.sigle))

  final case class SourceRetrieved(
      source: Source,
  )
  given Encoder[SourceRetrieved] =
    Encoder.forProduct2("kind", "source")(si => ("SourceRetrieved", si.source))

  final case class SourceCreated(
      id: Int,
  )
  given Encoder[SourceCreated] =
    Encoder.forProduct2("kind", "id")(si => ("SourceCreated", si.id))

  final case class DocumentsRetrieved(
      documents: List[Document],
  )
  given Encoder[DocumentsRetrieved] =
    Encoder.forProduct2("kind", "documents")(si => ("DocumentsRetrieved", si.documents))

  case object DocumentNotFound
  given Encoder[DocumentNotFound.type] =
    Encoder.instance(_ => Json.obj(("kind", Json.fromString("DocumentNotFound"))))

  final case class DocumentRetrieved(
      document: Document,
  )
  given Encoder[DocumentRetrieved] =
    Encoder.forProduct2("kind", "document")(si => ("DocumentRetrieved", si.document))

  final case class DocumentCreated(
      id: Int,
  )
  given Encoder[DocumentCreated] =
    Encoder.forProduct2("kind", "id")(si => ("DocumentCreated", si.id))

  final case class NotesRetrieved(
      data: Json,
  )
  given Encoder[NotesRetrieved] =
    Encoder.forProduct2("kind", "data")(nr => ("NotesRetrieved", nr.data))

  final case class UploadFinished(
      errors: List[String],
  )
  given Encoder[UploadFinished] =
    Encoder.forProduct2("kind", "errors")(uf => ("UploadFinished", uf.errors))

