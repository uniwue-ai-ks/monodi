package de.olyro.monodi.exprt
package print

import de.olyro.monodi.data.Document

final case class HeaderInfo(
    textInitium: String,
    gattung1: String,
    bibVerweis: String,
    dokId: String,
    editor: String,
)

object HeaderInfo:
  def fromDocument(doc: Document, editorMap: Map[String, String]): HeaderInfo =
    HeaderInfo(
      doc.textinitium,
      doc.gattung1,
      doc.bibliographischerverweis,
      doc.dokumenten_id,
      editorMap.getOrElse(doc.additionalData.getOrElse("Editor", "-"), "-"),
    )
