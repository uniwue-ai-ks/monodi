package de.olyro.monodi.`exprt`.renderer

import de.olyro.monodi.data.notes.BaseNote

object Config:
  val noteHeight      = 10.0
  val noteWidth       = 11.0
  val liquescentScale = 0.7

  val upperStaffPitch  = BaseNote.F.halfs(5)
  val middleStaffPitch = BaseNote.B.halfs(4)
  val lowerStaffPitch  = BaseNote.E.halfs(4)

  val ledgerLineJutLeft  = 3.0
  val ledgerLineJutRight = 2.5

  val fontSize      = 16f
  val smallFontSize = 10f

  val vBarHeight = 20.0

  val linePartPadding      = 16.0
  val syllablePadding      = linePartPadding
  val lineTopPadding       = 60.0 // G6 - B4 (middle staff line)
  val endOfDocumentPadding = 10.0
  val textPartPadding      = 1.0
  val textLinePadding      = 5.0

  val signatureWidth = 40.0
  val folioTextWidth = 70.0

  // vertical distance between overlapping paratexts in single line mode
  val paratextOverlapPadding = 20.0
