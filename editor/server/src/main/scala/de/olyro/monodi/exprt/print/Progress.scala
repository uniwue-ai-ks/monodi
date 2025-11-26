package de.olyro.monodi
package exprt.print

enum Progress derives io.circe.derivation.ConfiguredCodec:
  case StartingUp
  case LoadingNotes(document: Int)
  case Layouting(page: Int, stage: Int)
  case AddingLineNumbers
  case AddingToc
  case BoxToSvg(page: Int)
  case ConvertingSvgToPdf
