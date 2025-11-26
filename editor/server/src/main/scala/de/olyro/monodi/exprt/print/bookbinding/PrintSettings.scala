package de.olyro.monodi.exprt.print.bookbinding

import de.olyro.monodi.exprt.CanvasConfig
import de.olyro.monodi.Util

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
    addCriticalApparatus: Boolean
) derives io.circe.Codec.AsObject:
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
