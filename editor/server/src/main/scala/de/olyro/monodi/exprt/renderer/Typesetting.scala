package de.olyro.monodi.`exprt`.renderer

import de.olyro.monodi.`exprt`.FontKind

import java.awt.font.{FontRenderContext, TextLayout}
import java.awt.geom.AffineTransform

object Typesetting:
  sealed trait Placement derives CanEqual
  object Placement:
    case object Normal      extends Placement
    case object Subscript   extends Placement
    case object Superscript extends Placement

  final case class Formatting(fontKind: FontKind, hidden: Boolean, placement: Placement)

  private case class ProtoBox(width: String => Double, height: Double, yAnchor: Double, xAnchor: String => Double)

  private object Cache:
    val baseFontPlain  = de.olyro.monodi.`exprt`.TextBox.fontNormal
    val baseFontItalic = de.olyro.monodi.`exprt`.TextBox.fontItalic
    val baseFontSmcp   = de.olyro.monodi.`exprt`.TextBox.fontSmallCaps

    val context = new FontRenderContext(new AffineTransform(), true, true)

    val protoBoxes: Map[(FontKind, Placement), ProtoBox] = (for
      fk <- Iterator(FontKind.Normal, FontKind.Italic, FontKind.Caps)
      pl <- Iterator(Placement.Normal, Placement.Subscript, Placement.Superscript)
    yield {
      val baseFont = fk match {
        case FontKind.Caps   => baseFontSmcp
        case FontKind.Italic => baseFontItalic
        case FontKind.Normal => baseFontPlain
      }
      val m        = baseFont.getLineMetrics("x", context)
      val h        = (m.getAscent + m.getDescent).toDouble
      val y        = m.getAscent + (pl match {
        case Placement.Normal      => 0
        case Placement.Superscript => -0.28 * h
        case Placement.Subscript   => 0.14 * h
      })
      val font     = pl match {
        case Placement.Normal                            => baseFont
        case Placement.Subscript | Placement.Superscript => baseFont.deriveFont(Config.smallFontSize)
      }
      val width    = (s: String) => new TextLayout(s, font, context).getBounds.getWidth
      val xAnchor  = (s: String) => -new TextLayout(s, font, context).getBounds.getX
      ((fk, pl), ProtoBox(width, h, y, xAnchor))
    }).toMap


  import Cache.*

  val space      = new TextLayout(" ", baseFontPlain, context).getAdvance.toDouble
  val smallSpace = new TextLayout(" ", baseFontPlain.deriveFont(Config.smallFontSize), context).getAdvance.toDouble

  def textHeight(fmt: Formatting)                = protoBoxes((fmt.fontKind, fmt.placement)).height
  def textYAnchor(fmt: Formatting)               = protoBoxes((fmt.fontKind, fmt.placement)).yAnchor
  def textWidth(fmt: Formatting, text: String)   = protoBoxes((fmt.fontKind, fmt.placement)).width(text)
  def textXAnchor(fmt: Formatting, text: String) = protoBoxes((fmt.fontKind, fmt.placement)).xAnchor(text)

  val fmtNormal      = Formatting(FontKind.Normal, false, Placement.Normal)
  val normalBaseline = protoBoxes((FontKind.Normal, Placement.Normal)).yAnchor
