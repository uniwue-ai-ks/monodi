package de.olyro.monodi
package data
package notes

import io.circe.*
sealed trait DocumentType derives CanEqual:
  import DocumentType.*
  def maxDepth: Int =
    this match
      case Level1 => 1
      case Level2 => 2
      case Level3 => 3

object DocumentType:
  case object Level1 extends DocumentType
  case object Level2 extends DocumentType
  case object Level3 extends DocumentType

  given documentTypeDecoder: Decoder[DocumentType] = Decoder[String].emap(_ match {
    case "Level1" => Right(Level1)
    case "Level2" => Right(Level2)
    case "Level3" => Right(Level3)
    case x        => Left(s"Could not parse >>$x<< as a documentType")
  })

  given documentTypeEncoder: Encoder[DocumentType] = Encoder[String].contramap[DocumentType](_.toString)
