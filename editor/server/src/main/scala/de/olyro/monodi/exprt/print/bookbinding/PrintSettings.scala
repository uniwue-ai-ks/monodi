package de.olyro.monodi.exprt.print.bookbinding

import de.olyro.monodi.exprt.CanvasConfig
import de.olyro.monodi.Util
import io.circe.derivation.{Configuration, ConfiguredCodec}

// A default value on a field means "wire default": when an old client sends
// its print settings, the field is missing from the JSON, and decoding fills
// in the default. Old clients are not hypothetical — the testing UI and the
// published UI share this backend, so one of the two always runs the older
// bundle. Don't add defaults for convenience.
final case class PrintSettings(
    paperWidthInMm: Double,
    paperHeightInMm: Double,
    marginTopInMm: Double,
    marginRightInMm: Double,
    marginBottomInMm: Double,
    marginLeftInMm: Double,
    zoomFactor: Double,
    paratextWordPadding: Double,
    paratextLinePadding: Double,
    lineContinuationIndent: Double,
    linePartPadding: Double,
    linePadding: Double,
    liquescentScale: Double,
    noteLineJutLeft: Double,
    noteLineJutRight: Double,
    endOfDocumentPadding: Double,
    additionalContainerLevelDifferencePadding: Double,
    drawCommentMarks: Boolean,
    commentMarkerXOffsetRelative: Double,
    commentMarkerYOffsetRelative: Double,
    synopsisParatextYPadding: Double,
    signatureWidth: Double,
    folioTextWidth: Double,
    syllableFontSize: Double,
    drawPageNumbers: Boolean,
    drawTableOfContents: Boolean,
    addSourceDescriptions: Boolean,
    addCriticalApparatus: Boolean,
    addNotationDocumentation: Boolean = false
):
  lazy val canvasSettings: CanvasConfig =
    val unclampedWidth  = (paperWidthInMm - marginLeftInMm - marginRightInMm) * Util.mmToSvgUnitsConstant / zoomFactor
    val unclampedHeight = (paperHeightInMm - marginTopInMm - marginBottomInMm) * Util.mmToSvgUnitsConstant / zoomFactor

    // the software can't handle canvases below a certain size
    val width  = Util.clamp(unclampedWidth, 400.0, unclampedWidth)
    val height = Util.clamp(unclampedHeight, 400.0, unclampedHeight)

    CanvasConfig(
      embedFonts = false,
      useSystemFontNames = true,
      width = width,
      height = height,
      paratextWordPadding = paratextWordPadding,
      paratextLinePadding = paratextLinePadding,
      lineContinuationIndent = lineContinuationIndent,
      linePartPadding = linePartPadding,
      linePadding = linePadding,
      liquescentScale = liquescentScale,
      noteLineJutLeft = noteLineJutLeft,
      noteLineJutRight = noteLineJutRight,
      endOfDocumentPadding = endOfDocumentPadding,
      additionalContainerLevelDifferencePadding = additionalContainerLevelDifferencePadding,
      drawCommentMarks = drawCommentMarks,
      commentMarkerXOffsetRelative = commentMarkerXOffsetRelative,
      commentMarkerYOffsetRelative = commentMarkerYOffsetRelative,
      synopsisParatextYPadding = synopsisParatextYPadding,
      signatureWidth = signatureWidth,
      folioTextWidth = folioTextWidth,
      syllableFontSize = syllableFontSize
    )

  lazy val printSettings = de.olyro.monodi.exprt.print.Settings(
    pageNumberPrefix = Some("").filter(_ => drawPageNumbers)
  )

object PrintSettings:
  // scoped here so it cannot leak into other ConfiguredCodec derivations in
  // this package, which use the package-wide discriminator Configuration
  private given Configuration                         = Configuration.default.withDefaults
  given codec: io.circe.Codec.AsObject[PrintSettings] = ConfiguredCodec.derived
