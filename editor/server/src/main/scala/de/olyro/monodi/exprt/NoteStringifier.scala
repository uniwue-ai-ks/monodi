package de.olyro.monodi
package exprt

import de.olyro.monodi.data.notes.BaseNote
import de.olyro.monodi.data.notes.Note
import cats.kernel.Comparison

object NoteStringifier:
  def nameNote(n: Note): Option[String] = (n.octave, n.base) match
    case (2, BaseNote.A) => Some("AA")
    case (2, BaseNote.B) => Some("BB")
    case (3, BaseNote.C) => Some("CC")
    case (3, BaseNote.D) => Some("DD")
    case (3, BaseNote.E) => Some("EE")
    case (3, BaseNote.F) => Some("FF")
    case (3, BaseNote.G) => Some("Γ")
    case (3, BaseNote.A) => Some("A")
    case (3, BaseNote.B) => Some("B")
    case (4, BaseNote.C) => Some("C")
    case (4, BaseNote.D) => Some("D")
    case (4, BaseNote.E) => Some("E")
    case (4, BaseNote.F) => Some("F")
    case (4, BaseNote.G) => Some("G")
    case (4, BaseNote.A) => Some("a")
    case (4, BaseNote.B) => Some("b")
    case (5, BaseNote.C) => Some("c")
    case (5, BaseNote.D) => Some("d")
    case (5, BaseNote.E) => Some("e")
    case (5, BaseNote.F) => Some("f")
    case (5, BaseNote.G) => Some("g")
    case (5, BaseNote.A) => Some("aa")
    case (5, BaseNote.B) => Some("bb")
    case (6, BaseNote.C) => Some("cc")
    case (6, BaseNote.D) => Some("dd")
    case (6, BaseNote.E) => Some("ee")
    case (6, BaseNote.F) => Some("ff")
    case (6, BaseNote.G) => Some("gg")
    case (6, BaseNote.A) => Some("aaa")
    case _               => None

  def directions(notes: List[Note]) =
    if notes.size < 2 then ""
    else
      notes
        .sliding(2, 1)
        .map(_.toList match {
          case x :: y :: Nil =>
            Note.compare(x, y) match {
              case Comparison.LessThan    => "u"
              case Comparison.EqualTo     => "e"
              case Comparison.GreaterThan => "d"
            }
          case _ => sys.error("Can never happen")
        })
        .mkString("")
        .replaceAll("e+", "e")

  def intervals(notes: List[Note]) =
    if notes.size < 2 then ""
    else
      notes
        .sliding(2, 1)
        .map(_.toList match {
          case x :: y :: Nil =>
            Note.compare(x, y) match {
              case Comparison.LessThan    => (y.base.halfs(y.octave) - x.base.halfs(x.octave)).toString + "u"
              case Comparison.GreaterThan => (x.base.halfs(x.octave) - y.base.halfs(y.octave)).toString + "d"
              case Comparison.EqualTo     => "e"
            }
          case _ => sys.error("Can never happen")
        })
        .mkString("")
        .replaceAll("e+", "e")

