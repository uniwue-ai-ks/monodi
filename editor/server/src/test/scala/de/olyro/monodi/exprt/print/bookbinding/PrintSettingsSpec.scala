package de.olyro.monodi
package exprt.print.bookbinding

import io.circe.syntax.*
import zio.test.*
import zio.test.Assertion.*

object PrintSettingsSpec extends ZIOSpecDefault {
  private val settings = PrintSettings(
    paperWidthInMm = 210,
    paperHeightInMm = 297,
    marginTopInMm = 14,
    marginRightInMm = 20,
    marginBottomInMm = 14,
    marginLeftInMm = 20,
    zoomFactor = 0.6,
    paratextWordPadding = 5.0,
    paratextLinePadding = 5.0,
    lineContinuationIndent = 50.0,
    linePartPadding = 16.0,
    linePadding = 5.0,
    liquescentScale = 0.7,
    noteLineJutLeft = 3.0,
    noteLineJutRight = 2.5,
    endOfDocumentPadding = 10.0,
    additionalContainerLevelDifferencePadding = 30.0,
    drawCommentMarks = false,
    commentMarkerXOffsetRelative = 0.5,
    commentMarkerYOffsetRelative = 0.5,
    synopsisParatextYPadding = 3.0,
    signatureWidth = 50.0,
    folioTextWidth = 80.0,
    syllableFontSize = 20.0,
    drawPageNumbers = true,
    drawTableOfContents = true,
    addSourceDescriptions = true,
    addCriticalApparatus = true,
    addNotationDocumentation = true
  )

  def spec = suite("PrintSettings codec")(
    test("decodes JSON from stale clients that lack addNotationDocumentation as false") {
      val json = settings.asJson.mapObject(_.remove("addNotationDocumentation"))
      assert(json.as[PrintSettings].map(_.addNotationDocumentation))(isRight(equalTo(false)))
    },
    test("keeps a client-sent addNotationDocumentation value") {
      assert(settings.asJson.as[PrintSettings])(isRight(equalTo(settings)))
    },
    test("the wire-default Configuration does not leak into the package's discriminator codecs") {
      val status: Status = Status.Queued(3)
      assert(status.asJson.asObject.flatMap(_("kind")).flatMap(_.asString))(isSome(equalTo("Queued")))
    }
  )
}
