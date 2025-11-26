package de.olyro.monodi
package exprt

import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.font.TextLayout

import TextBox.Formatting.*
import scalatags.Text.all.*
import scalatags.Text.{svgAttrs as SA, svgTags as ST}

extension (f: java.awt.Font)
  def sameBaseFontAs(other: java.awt.Font): Boolean =
    f.getPSName == other.getPSName

object TextBox:

  private def base64enc(is: java.io.InputStream): String = java.util.Base64.getEncoder.encodeToString(is.readAllBytes())

  val normalFontPath = "ll.ttf"
  val italicFontPath = "llit.ttf"
  val smcpFontPath   = "llsc.ttf"

  val fontNormalBase64    = Util.unsafeWithResource(TextBox.getClass, normalFontPath, base64enc)
  val fontItalicBase64    = Util.unsafeWithResource(TextBox.getClass, italicFontPath, base64enc)
  val fontSmallCapsBase64 = Util.unsafeWithResource(TextBox.getClass, smcpFontPath, base64enc)

  val normalFontSystemName = "Linux Libertine"
  val smcpFontSystemName   = "Linux Libertine Capitals"

  val fontNormal     = Util
    .unsafeWithResource(TextBox.getClass, normalFontPath, java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, _))
    .deriveFont(0, 16f)
  val fontHeaderInfo = Util
    .unsafeWithResource(TextBox.getClass, normalFontPath, java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, _))
    .deriveFont(0, 20f)
  val fontItalic     = Util
    .unsafeWithResource(TextBox.getClass, italicFontPath, java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, _))
    .deriveFont(0, 16f)
    .deriveFont(java.awt.Font.ITALIC)
  val fontSmallCaps  = Util
    .unsafeWithResource(TextBox.getClass, smcpFontPath, java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, _))
    .deriveFont(0, 16f)

  private def trim(s: String): (Int, String, Int) =
    val pattern = """(?s)(\s*)(.+?)(\s*)""".r

    s match
      case s if s.isBlank         => (s.length, "", 0)
      case pattern(pre, res, suf) => (pre.length, res, suf.length)

  private def parse(s: String, fmt: Formatting, id: Option[String], setMetricMarkers: Boolean): BoundingBox =
    val pattern = """(.*?)([\^_])(\w+)(.*)""".r

    @scala.annotation.tailrec
    def go(s: String, acc: Vector[BoundingBox]): Vector[BoundingBox] = s match
      case pattern(head, "^", arg, tail) =>
        go(tail, acc :+ trimText(head, fmt, setMetricMarkers) :+ raw(arg, None, fmt.superscript, setMetricMarkers))
      case pattern(head, "_", arg, tail) =>
        go(tail, acc :+ trimText(head, fmt, setMetricMarkers) :+ raw(arg, None, fmt.subscript, setMetricMarkers))
      case ""                            => acc
      case _                             => acc :+ trimText(s, fmt, setMetricMarkers)

    BoundingBox.concatX(go(s, Vector.empty).toList, 1.0, id)

  private def trimText(text: String, fmt: Formatting, setMetricMarkers: Boolean): BoundingBox =
    val (pre, word, suf) = trim(text)
    TextBox.raw(word, None, fmt, setMetricMarkers).padLeft(pre * fmt.space).padRight(suf * fmt.space)

  final case class Formatting(
      font: java.awt.Font,
      opacity: Double,
      style: Option[String],
      subSup: Option[SubSup],
      useSystemNames: Boolean
  ):
    lazy val ctxt    = new FontRenderContext(new AffineTransform(), true, true)
    lazy val metrics = font.getLineMetrics("x", ctxt)
    lazy val space   = new TextLayout(" ", font, ctxt).getAdvance.toDouble

    def subscript: Formatting                  = this.copy(subSup = Some(Subscript))
    def superscript: Formatting                = this.copy(subSup = Some(Superscript))
    def withUseSystemFontNames(use: Boolean)   = this.copy(useSystemNames = use)
    def withFontSize(size: Double): Formatting = this.copy(font = font.deriveFont(size.toFloat))

  object Formatting:
    sealed trait SubSup derives CanEqual
    case object Superscript extends SubSup
    case object Subscript   extends SubSup

    val normal    = Formatting(fontNormal, 1, None, None, false)
    val smallCaps = Formatting(fontSmallCaps, 1, None, None, false)
    val italic    = Formatting(fontItalic, 1, Some("italic"), None, false)
    val hidden    = Formatting(fontNormal, 0, None, None, false)

  def normal(
      textValue: String,
      useSystemFontNames: Boolean,
      id: Option[String] = None,
      setMetricMarkers: Boolean = false,
      fontSize: Option[Double] = None
  ): BoundingBox =
    parseHelper(Formatting.normal, textValue, useSystemFontNames, id, setMetricMarkers, fontSize)

  def smallCaps(
      textValue: String,
      useSystemFontNames: Boolean,
      id: Option[String] = None,
      setMetricMarkers: Boolean = false,
      fontSize: Option[Double] = None
  ): BoundingBox =
    parseHelper(Formatting.smallCaps, textValue, useSystemFontNames, id, setMetricMarkers, fontSize)

  def italic(
      textValue: String,
      useSystemFontNames: Boolean,
      id: Option[String] = None,
      setMetricMarkers: Boolean = false,
      fontSize: Option[Double] = None
  ): BoundingBox =
    parseHelper(Formatting.italic, textValue, useSystemFontNames, id, setMetricMarkers, fontSize)

  private def parseHelper(
      base: Formatting,
      textValue: String,
      useSystemFontNames: Boolean,
      id: Option[String],
      setMetricMarkers: Boolean,
      fontSize: Option[Double]
  ): BoundingBox =
    val withSystemFontsSet = base.copy(useSystemNames = useSystemFontNames)
    val newFormatting      =
      fontSize.fold(withSystemFontsSet)(withSystemFontsSet.withFontSize)
    parse(textValue, newFormatting, id, setMetricMarkers)

  def raw(
      text: String,
      id: Option[String],
      fmt: Formatting,
      setMetricMarkers: Boolean
  ): BoundingBox =
    val nominalHeight = fmt.metrics.getAscent + fmt.metrics.getDescent
    val baseline      = fmt.metrics.getAscent
    val baselineBB    = BoundingBox(0, 0, Nil, Some("baseline"), full = false)
    val ascentBB      = BoundingBox(0, 0, Nil, Some("ascent"), full = false)
    val descentBB     = BoundingBox(0, 0, Nil, Some("descent"), full = false)

    val textBoundingBox =
      if text.isEmpty then BoundingBox(0, nominalHeight.toDouble, Nil)
      else
        val fontName   =
          if fmt.useSystemNames then
            if fmt.font.sameBaseFontAs(fontSmallCaps) then smcpFontSystemName else normalFontSystemName
          else if fmt.font.sameBaseFontAs(fontNormal) || fmt.font.sameBaseFontAs(fontItalic) then "TextNormal"
          else "TextSmallCaps"
        val fontSize   = if fmt.subSup.isDefined then fmt.font.getSize * 0.8 else fmt.font.getSize
        val fontSizePx = s"${fontSize}px"
        val font       = if fmt.subSup.isDefined then fmt.font.deriveFont(10f) else fmt.font
        val box        = new TextLayout(text, font, fmt.ctxt).getBounds
        val dx         = -box.getX
        val dy         = fmt.metrics.getAscent + (fmt.subSup match {
          case None              => 0
          case Some(Superscript) => -0.28 * nominalHeight
          case Some(Subscript)   => 0.14 * nominalHeight
        })

        BoundingBox(
          box.getWidth,
          nominalHeight.toDouble,
          List(
            Sub(
              Point(0, 0),
              Right(p =>
                ST.text(
                  SA.fontFamily  := fontName,
                  SA.fontSize    := fontSizePx,
                  SA.x           := (p.x + dx),
                  SA.y           := (p.y + dy),
                  SA.fillOpacity := fmt.opacity,
                  fmt.style.map(SA.attr("font-style") := _)
                )(text),
              )
            )
          ),
          id,
          full = true
        )

    if setMetricMarkers then
      textBoundingBox
        .addAt(baselineBB, Point(0, baseline.toDouble))
        .addAt(ascentBB, Point(0, 0))
        .addAt(descentBB, Point(0, nominalHeight.toDouble))
    else textBoundingBox

  val emptyBaseline = BoundingBox(0, 0, Nil, Some("baseline"), full = false)
