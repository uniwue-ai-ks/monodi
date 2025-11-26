package de.olyro.monodi.exprt
package print.bookbinding

final case class Request(
    customCover: Option[CustomCover],
    documents: List[String],
    printSettings: PrintSettings
) derives io.circe.Codec.AsObject
