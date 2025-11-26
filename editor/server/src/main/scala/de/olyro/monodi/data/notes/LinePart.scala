package de.olyro.monodi
package data
package notes

sealed trait LinePart derives io.circe.derivation.ConfiguredCodec:
  def uuid: String
  def setUuid(uuid: String): LinePart = this match
    case lp: Syllable    => lp.copy(uuid = uuid)
    case lp: LineChange  => lp.copy(uuid = uuid)
    case lp: FolioChange => lp.copy(uuid = uuid)
    case lp: Clef        => lp.copy(uuid = uuid)
    case lp: Box         => lp.copy(uuid = uuid)

final case class Syllable(
    uuid: String,
    text: String,
    notes: Spaced,
    syllableType: SyllableType,
) extends LinePart:
  def getAllNotes: List[Note] = notes.spaced.flatMap(_.nonSpaced.flatMap(_.grouped))

final case class LineChange(
    uuid: String,
    hasNotes: Boolean,
    focus: Boolean,
) extends LinePart

final case class FolioChange(
    uuid: String,
    hasNotes: Boolean,
    focus: Boolean,
    text: String,
) extends LinePart

final case class Clef(
    uuid: String,
    focus: Boolean,
    base: BaseNote,
    octave: Int,
    shape: String,
) extends LinePart

final case class Box(
    uuid: String,
    focus: Boolean,
) extends LinePart

object LinePart:
  def getAllNotes(l: LinePart): List[Note] =
    l match
      case s: Syllable    => s.getAllNotes
      case _: LineChange  => Nil
      case _: FolioChange => Nil
      case _: Clef        => Nil
      case _: Box         => Nil
