package de.olyro.monodi
package exprt
package print

import de.olyro.monodi.data.notes.Justification

enum LineItem derives io.circe.derivation.ConfiguredCodec:
  case VSkip(size: Size)
  case Text(text: String, size: Size, color: Option[String], justification: Option[Justification])

object LineItem:
  object Text:
    def apply(text: String, size: Size): LineItem                = Text(text, size, None, None)
    def apply(text: String, size: Size, color: String): LineItem = Text(text, size, Some(color), None)
