package de.olyro.monodi
package data
package notes

import io.circe.*

sealed trait ParatextType

object ParatextType:
  case object Festtag      extends ParatextType
  case object Feier        extends ParatextType
  case object Gesang       extends ParatextType
  case object Formteil     extends ParatextType
  case object Aufführung   extends ParatextType
  case object Melodiename  extends ParatextType
  case object Zuschreibung extends ParatextType

  given paretextTypeDecoder: Decoder[ParatextType] = Decoder[String].emap(_ match {
    case "Festtag"      => Right(Festtag)
    case "Feier"        => Right(Feier)
    case "Gesang"       => Right(Gesang)
    case "Formteil"     => Right(Formteil)
    case "Aufführung"   => Right(Aufführung)
    case "Melodiename"  => Right(Melodiename)
    case "Zuschreibung" => Right(Zuschreibung)
    case x              => Left(s"Could not parse >>$x<< as a paratextType")
  })

  given paratextTypeEncoder: Encoder[ParatextType] = Encoder[String].contramap[ParatextType](_.toString)
