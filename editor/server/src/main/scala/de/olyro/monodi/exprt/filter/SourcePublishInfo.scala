package de.olyro.monodi.exprt.filter

/**
  * All the info that is needed to decide whether a source should be published.
  *
  * @param id The id of the source, not the quellensigle, the id
  * @param publish The value of the publish field of the source
  */
final case class SourcePublishInfo(
    id: String,
    publish: String
)
