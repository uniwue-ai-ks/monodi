package de.olyro.monodi
package data
package notes

import cats.kernel.Comparison
final case class Note(
    uuid: String,
    noteType: NoteType,
    base: BaseNote,
    liquescent: Boolean,
    octave: Int,
    focus: Boolean,
) derives io.circe.Codec.AsObject:
  def halfs = base.halfs(octave)

object Note:
  def compare(n1: Note, n2: Note): Comparison =
    val h1 = n1.halfs
    val h2 = n2.halfs

    if h1 < h2 then Comparison.LessThan
    else if h1 == h2 then Comparison.EqualTo
    else Comparison.GreaterThan

  given ordering: Ordering[Note] = new Ordering[Note]:
    def compare(x: Note, y: Note): Int = Note.compare(x, y).toInt
