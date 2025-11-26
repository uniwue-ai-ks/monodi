package de.olyro.monodi
package upload

import de.olyro.monodi.data.Source

final case class ParsedSource(
  id: String,
  meta: Option[Source],
  documents: List[ParsedDocument],
)
