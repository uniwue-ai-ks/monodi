package de.olyro.monodi.exprt.renderer

import de.olyro.monodi.exprt.renderer.Typesetting.Formatting
import de.olyro.monodi.data.notes.Spaced

sealed trait Line:
  def signature: Box
  def folioLabel: Option[FolioLabel]
final case class NotesLine(signature: Box, folioLabel: Option[FolioLabel], segments: List[LineSegment]) extends Line

final case class Grid(lines: List[GridLine])
sealed trait GridLine derives CanEqual
case object MissingLine extends GridLine
final case class SynopsisLine(
  signature: Box,
  folioLabel: Option[FolioLabel],
  header: List[Box],
  segments: List[LineSegment]
)                       extends GridLine

final case class FolioLabel(box: Box, anchorId: String)

final case class LineSegment(header: Option[Box], top: Box, bottom: Box, width: Double, xAnchor: Double, staffLines: Boolean):
  lazy val yAnchor = top.yAnchor

object LineSegment:
  def apply(header: Option[Box], top: Box, bottom: Box, staffLines: Boolean): LineSegment =
    LineSegment(header, top, bottom, top.width max bottom.width, 0.0, staffLines)

  def hSpace(width: Double, staffLines: Boolean): LineSegment =
    LineSegment(None, Box.empty, Box.empty, width, 0.0, staffLines)

sealed trait Box derives CanEqual:
  def width: Double
  def height: Double
  def yAnchor: Double

object Box:
  val empty = BlankBox(0, 0, 0)

final case class NotesBox(notes: Spaced) extends Box:
  override lazy val (height, yAnchor) = Notesetting.heightAndYAnchor(notes)
  override lazy val width: Double     = Notesetting.width(notes)

final case class TextBox(text: String, formatting: Formatting) extends Box:
  override lazy val width: Double   = Typesetting.textWidth(formatting, text)
  override lazy val height: Double  = Typesetting.textHeight(formatting)
  override lazy val yAnchor: Double = Typesetting.textYAnchor(formatting)

  lazy val xAnchor: Double = Typesetting.textXAnchor(formatting, text)

final case class MultiTextBox(boxes: List[Box], height: Double, yAnchor: Double) extends Box:
  override lazy val width: Double =
    boxes.map(_.width).sum + (if boxes.nonEmpty then (boxes.size - 1) * Config.textPartPadding else 0.0)

object MultiTextBox:
  def apply(b: TextBox, bs: List[Box]): MultiTextBox = MultiTextBox(b :: bs, b.height, b.yAnchor)

final case class MultiLineTextBox(boxes: List[Box]) extends Box:
  override lazy val width: Double   = boxes.map(_.width).maxOption getOrElse 0.0
  override lazy val height: Double  =
    if boxes.nonEmpty then boxes.map(_.height).sum + (boxes.size - 1) * Config.textLinePadding else 0.0
  override lazy val yAnchor: Double = boxes.headOption.map(_.yAnchor) getOrElse 0.0

final case class CommentMarksBox(left: Boolean, inner: Box, right: Boolean) extends Box:
  override def width: Double   = inner.width
  override def height: Double  = inner.height
  override def yAnchor: Double = inner.yAnchor

final case class BlankBox(width: Double, height: Double, yAnchor: Double) extends Box

sealed trait VBarBox extends Box:
  override val width: Double   = 10.0
  override val height: Double  = 20.0
  override val yAnchor: Double = Typesetting.normalBaseline // fixme: is this backwards compatible?

case object LineChangeBox  extends VBarBox
case object FolioChangeBox extends VBarBox
