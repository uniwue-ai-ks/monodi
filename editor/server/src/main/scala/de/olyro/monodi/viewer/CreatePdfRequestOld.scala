package de.olyro.monodi.viewer

import CreatePdfRequestOld.*
import de.olyro.monodi.exprt.print.bookbinding.*

final case class CreatePdfRequestOld(
    documents: List[String],
    printConfig: PrintSettingsOld
) derives io.circe.Codec.AsObject:
  def toNew: Request = Request(
    customCover = None,
    documents = documents,
    printSettings = printConfig.toPrintSettings
  )

object CreatePdfRequestOld:
  final case class PrintSettingsOld(
      embedFonts: Boolean,
      useSystemFontNames: Boolean,
      paperWidth: Double,
      paperHeight: Double,
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
      folioTextWidth: Double
  ) derives io.circe.Codec.AsObject:
    def toPrintSettings: PrintSettings = PrintSettings(
      paperWidthInMm = paperWidth * 0.2645833333,
      paperHeightInMm = paperHeight * 0.2645833333,
      marginTopInMm = 14,
      marginRightInMm = 10,
      marginBottomInMm = 20,
      marginLeftInMm = 10,
      zoomFactor = 1,
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
      syllableFontSize = 16,
      drawPageNumbers = false,
      drawTableOfContents = false,
      addSourceDescriptions = false,
      addCriticalApparatus = false
    )
