package de.olyro.monodi.`exprt`

import de.olyro.monodi.data.notes.{BaseNote, Note, NoteType}
import scalatags.Text.all.raw

/** !!!ATTENTION!!! After changing or updating the varbiables of the Config you have to change the Interface
  * PrintSettings in the Frontend to match your new changes
  */
case class CanvasConfig(
    embedFonts: Boolean = false,
    /** This variable controls how the fonts should be referenced to.
      * For the libsvg export it is necessary to install the fonts in the docker
      * and refer to them via their system names, where as for the export for
      * the website, they are referred by their name and include into the svg
      * or loaded externally.
      *
      * this never changes which fonts are used, only how they are referred to.
      */
    useSystemFontNames: Boolean = true,
    width: Double = 770.0,
    height: Double = (770.0 * Math.sqrt(2)),
    paratextWordPadding: Double = 5.0,
    paratextLinePadding: Double = 5.0,
    lineContinuationIndent: Double = 50.0,
    linePartPadding: Double = 16.0,
    linePadding: Double = 5.0,
    liquescentScale: Double = 0.7,
    noteLineJutLeft: Double = 3.0,
    noteLineJutRight: Double = 2.5,
    endOfDocumentPadding: Double = 10.0,
    additionalContainerLevelDifferencePadding: Double = 30.0,
    drawCommentMarks: Boolean = false,
    commentMarkerXOffsetRelative: Double = 0.5,
    commentMarkerYOffsetRelative: Double = 0.5,
    synopsisParatextYPadding: Double = 3.0,
    signatureWidth: Double = 40.0,
    folioTextWidth: Double = 70.0,
    syllableFontSize: Double = 16.0
) derives io.circe.Codec.AsObject:

  val maxNote = BaseNote.G.halfs(6)
  val minNote = BaseNote.G.halfs(3)

  val note       = BoundingBox(11, 10, List(use("note-template")), Some("note"), full = true)
  val oriscus    = BoundingBox(12, 10, List(use("oriscus-template")), Some("note oriscus"), full = true)
  val flat       = BoundingBox(6.2, 15, List(use("flat-template")), Some("note flat"), full = true)
  val sharp      = BoundingBox(6.4, 16, List(use("sharp-template")), Some("note sharp"), full = true)
  val strophicus = BoundingBox(6.7, 10, List(use("strophicus-template")), Some("note strophicus"), full = true)
  val natural    = BoundingBox(5.5, 19.4, List(use("natural-template")), Some("note natural"), full = true)
  val quilisma   = BoundingBox(11.6, 7.2, List(use("quilisma-template")), Some("note quilisma"), full = true)

  def commentL(onClickMessage: String) =
    val clickable = clickableTransparentSquare(25, -5, -5, onClickMessage)
    BoundingBox(10, 10, List(use("corner-bottom-left"), clickable), Some("comment marker left"), full = true)

  def commentR(onClickMessage: String) =
    val clickable = clickableTransparentSquare(25, -5, -5, onClickMessage)
    BoundingBox(10, 10, List(use("corner-bottom-right"), clickable), Some("comment marker right"), full = true)

  private def clickableTransparentSquare(
      sideLength: Double,
      offsetX: Double,
      offsetY: Double,
      onClickMessage: String
  ): Sub =
    Sub(
      Point(offsetX, offsetY),
      Right(pp => raw(s"""
        <rect
          class="clickable"
          onclick='window.parent.postMessage("$onClickMessage", "*")'
          x="${pp.x.toString}"
          y="${pp.y.toString}"
          width="${sideLength.toString}"
          height="${sideLength.toString}"
          fill="transparent"
        />"""))
    )

  private def use(name: String): Sub =
    Sub(
      Point(0, 0),
      Right(pp => raw(s"""<use x="${pp.x.toString}" y="${pp.y.toString}" xlink:href="#$name"/>"""))
    )

  def spacedNoteSpace(n1: Note, n2: Note): Double    = note.width * 6 / 4
  def nonSpacedNoteSpace(n1: Note, n2: Note): Double =
    if n1.noteType == NoteType.Flat || n1.noteType == NoteType.Sharp then note.width * 1 / 4
    else groupNoteSpace(n1, n2)

  def groupNoteSpace(n1: Note, n2: Note): Double =
    val h1     = n1.base.halfs(n1.octave)
    val h2     = n2.base.halfs(n2.octave)
    val diff   = h2 - h1
    val factor =
      if diff <= -2 then 0.913
      else if diff <= -1 then 0.938
      else if diff <= 0 then 1
      else if diff <= 1 then 0.975
      else if diff <= 2 then 0.953
      else if diff <= 3 then 0.925
      else if diff <= 4 then 0.900
      else 0.900

    note.width * 3 / 4 * factor

  def withComments: CanvasConfig = copy(drawCommentMarks = true)
  def relativeSyllableFontScale  = syllableFontSize / 16.0

object CanvasConfig:
  val defaultConfig = new CanvasConfig(false, false)
