package de.olyro.monodi
package data
package notes

import io.circe.*

sealed trait SyllableType derives CanEqual

object SyllableType:
  case object Normal            extends SyllableType
  case object WithoutNotes      extends SyllableType
  case object EditorialEllipsis extends SyllableType
  case object SourceEllipsis    extends SyllableType

  given syllableTypeDec: Decoder[SyllableType] = Decoder[String].emap(_ match {
    case "Normal"            => Right(Normal)
    case "WithoutNotes"      => Right(WithoutNotes)
    case "EditorialEllipsis" => Right(EditorialEllipsis)
    case "SourceEllipsis"    => Right(SourceEllipsis)
    case x                   => Left(s"Could not parse >>$x<< as a syllableType")
  })

  given syllableTypeEnc: Encoder[SyllableType] = Encoder[String].contramap[SyllableType](_.toString)
