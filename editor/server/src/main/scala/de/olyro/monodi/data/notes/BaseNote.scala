package de.olyro.monodi
package data
package notes

import io.circe.*

sealed trait BaseNote derives CanEqual:
  def octaveOffset: Int =
    this match
      case BaseNote.C => 0
      case BaseNote.D => 1
      case BaseNote.E => 2
      case BaseNote.F => 3
      case BaseNote.G => 4
      case BaseNote.A => 5
      case BaseNote.B => 6

  def halfs(octave: Int): Int =
    7 * octave + octaveOffset

object BaseNote:
  case object C extends BaseNote
  case object D extends BaseNote
  case object E extends BaseNote
  case object F extends BaseNote
  case object G extends BaseNote
  case object A extends BaseNote
  case object B extends BaseNote

  given baseNoteDec: Decoder[BaseNote] = Decoder[String].emap(_ match {
    case "C" => Right(C)
    case "D" => Right(D)
    case "E" => Right(E)
    case "F" => Right(F)
    case "G" => Right(G)
    case "A" => Right(A)
    case "B" => Right(B)
    case x   => Left(s"Could not parse >>$x<< as a baseNote")
  })

  given baseNoteEnc: Encoder[BaseNote] = Encoder[String].contramap[BaseNote](_.toString)
