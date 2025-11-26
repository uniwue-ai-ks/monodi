package de.olyro.monodi
package upload

import de.olyro.monodi.data.Document
import io.circe.Json
import de.olyro.monodi.data.User

final case class ParsedDocument(
  id: String,
  meta: Option[Document],
  data: Option[Json],
  writers: Option[List[User]],
)
