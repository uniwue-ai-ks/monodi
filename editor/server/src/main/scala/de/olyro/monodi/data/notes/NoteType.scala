package de.olyro.monodi
package data
package notes

import io.circe.*

sealed trait NoteType derives CanEqual

object NoteType:
  case object Normal     extends NoteType
  case object Liquescent extends NoteType
  case object Ascending  extends NoteType
  case object Descending extends NoteType
  case object Oriscus    extends NoteType
  case object Quilisma   extends NoteType
  case object Strophicus extends NoteType
  case object Flat       extends NoteType
  case object Sharp      extends NoteType
  case object Natural    extends NoteType

  given noteTypeDec: Decoder[NoteType] = Decoder[String].emap(_ match {
    case "Normal"     => Right(Normal)
    case "Liquescent" => Right(Liquescent)
    case "Ascending"  => Right(Ascending)
    case "Descending" => Right(Descending)
    case "Oriscus"    => Right(Oriscus)
    case "Quilisma"   => Right(Quilisma)
    case "Strophicus" => Right(Strophicus)
    case "Flat"       => Right(Flat)
    case "Sharp"      => Right(Sharp)
    case "Natural"    => Right(Natural)
    case x            => Left(s"Could not parse >>$x<< as a noteType")
  })

  given noteTypeEnc: Encoder[NoteType] = Encoder[String].contramap[NoteType](_.toString)
