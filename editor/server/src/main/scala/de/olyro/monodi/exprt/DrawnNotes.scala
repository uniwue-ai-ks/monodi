package de.olyro.monodi
package exprt

import de.olyro.monodi.exprt.Pdf.PdfDoc

sealed trait DrawnNotes:
  def hash: String
  def width: Double

final case class DrawnSvg(
    hash: String,
    svg: String,
    width: Double
) extends DrawnNotes

final case class DrawnPdf(
    hash: String,
    pdf: PdfDoc,
    width: Double
) extends DrawnNotes

object DrawnNotes:
  def make(svg: Svg)(bb: BoundingBox): DrawnSvg =
    val drawn = svg.makeSVG(bb, "")
    DrawnSvg(Util.sha256(drawn), drawn, Math.ceil(bb.width))

  def makePdf(svg: Svg)(pages: List[BoundingBox]) = Pdf.default
    .printSvg(pages.map(svg.makeSVG(_, "")))
    .map(doc => DrawnPdf(Util.sha256(doc.data), doc, Math.ceil(pages.map(_.width).max)))
