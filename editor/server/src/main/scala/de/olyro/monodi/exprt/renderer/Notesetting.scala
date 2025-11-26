package de.olyro.monodi.exprt.renderer

import de.olyro.monodi.data.notes.*

object Notesetting:

  def heightAndYAnchor(notes: Spaced) =
    if notes.spaced.isEmpty then (0.0, 0.0)
    else
      val pitches = notes.spaced.flatMap(_.nonSpaced.flatMap(_.grouped match {
        case Nil           => Nil
        case single :: Nil => List(single.halfs)
        case all           =>
          val max = all.map(_.halfs).max
          val min = all.map(_.halfs).min
          List(min, max + 3 + (max % 2)) // min note, max note + tie
      }))
      val h       = (pitches.max - pitches.min + 2) * Config.noteHeight / 2
      val y       = (pitches.max + 1 - Config.middleStaffPitch) * Config.noteHeight / 2
      (h, y)

  def width(notes: Spaced): Double =
    case class NoteMeter(start: Note, width: Double, end: Note)

    def measureNonSpaced(nonSpaced: NonSpaced): Option[NoteMeter] =
      join(nonSpaced.nonSpaced.flatMap(measureGrouped), NonSpacedGap)

    def measureGrouped(grouped: Grouped): Option[NoteMeter] =
      join(grouped.grouped.map(n => NoteMeter(n, noteWidth(n), n)), GroupedGap)

    def join(l: List[NoteMeter], ng: NoteGap): Option[NoteMeter] = l match
      case Nil  => None
      case more =>
        Some(more.reduce[NoteMeter] { case (a, b) =>
          NoteMeter(a.start, a.width + ng.gap(a.end, b.start) + b.width, b.end)
        })

    join(notes.spaced.flatMap(measureNonSpaced), SpacedGap) map (_.width) getOrElse 0.0

  def noteWidth(n: Note): Double = (n.noteType match {
    case NoteType.Oriscus    => 12.0
    case NoteType.Quilisma   => 11.6
    case NoteType.Strophicus => 6.7
    case NoteType.Flat       => 6.2
    case NoteType.Sharp      => 6.4
    case NoteType.Natural    => 5.5
    case _                   => Config.noteWidth
  }) * (if n.liquescent then Config.liquescentScale else 1.0)

  def noteHeight(n: Note): Double = (n.noteType match {
    case NoteType.Oriscus    => 10.0
    case NoteType.Quilisma   => 7.2
    case NoteType.Strophicus => 10.0
    case NoteType.Flat       => 15.0
    case NoteType.Sharp      => 16.0
    case NoteType.Natural    => 19.4
    case _                   => Config.noteHeight
  }) * (if n.liquescent then Config.liquescentScale else 1.0)

  sealed trait NoteGap:
    def gap(a: Note, b: Note): Double

  case object SpacedGap extends NoteGap:
    override def gap(a: Note, b: Note): Double = Config.noteWidth * 6 / 4

  case object NonSpacedGap extends NoteGap:
    override def gap(a: Note, b: Note): Double =
      if a.noteType == NoteType.Flat || a.noteType == NoteType.Sharp then Config.noteWidth * 1 / 4
      else GroupedGap.gap(a, b)

  case object GroupedGap extends NoteGap:
    override def gap(a: Note, b: Note): Double =
      val diff   = b.halfs - a.halfs
      val factor =
        if diff <= -2 then 0.913
        else if diff <= -1 then 0.938
        else if diff <= 0 then 1
        else if diff <= 1 then 0.975
        else if diff <= 2 then 0.953
        else if diff <= 3 then 0.925
        else if diff <= 4 then 0.900
        else 0.900

      Config.noteWidth * 3 / 4 * factor
