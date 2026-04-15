package de.olyro.monodi.exprt.filter

import de.olyro.monodi.data.Document

/**
  * All the info that is needed to decide whether a document should be published.
  *
  * @param id The id of the document, not the dokumenten_id
  * @param publish The value of the publish field of the document
  * @param quelle_id The id of the source this document belongs to, not the quellensigle
  */
final case class DocPublishInfo(
    id: String,
    publish: String,
    quelle_id: String
)

object DocPublishInfo:
  def fromDoc(doc: Document): DocPublishInfo =
    DocPublishInfo(
      id = doc.id,
      publish = doc.publish,
      quelle_id = doc.quelle_id
    )
