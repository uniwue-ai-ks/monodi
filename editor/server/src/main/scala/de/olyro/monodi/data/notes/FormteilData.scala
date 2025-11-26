package de.olyro.monodi
package data
package notes

import io.circe.*

final case class FormteilData(
    name: FormteilData.FormteilDataName,
    data: String,
) derives Codec.AsObject

object FormteilData:
  sealed trait FormteilDataName derives CanEqual
  case object Signatur                  extends FormteilDataName
  case object Status                    extends FormteilDataName
  case object LemmatisiertesTextInitium extends FormteilDataName
  case object Verweis                   extends FormteilDataName

  object FormteilDataName:
    given formteilDataNameDecoder: Decoder[FormteilDataName] = Decoder[String].emap(_ match {
      case "Signatur"                  => Right(Signatur)
      case "Status"                    => Right(Status)
      case "LemmatisiertesTextInitium" => Right(LemmatisiertesTextInitium)
      case "Verweis"                   => Right(Verweis)
      case x                           => Left(s"Could not parse >>$x<< as a formteildataname")
    })

    given formteilDataNameEncoder: Encoder[FormteilDataName] = Encoder[String].contramap(_.toString)
