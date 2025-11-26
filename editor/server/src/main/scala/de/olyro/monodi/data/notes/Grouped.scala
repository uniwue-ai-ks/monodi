package de.olyro.monodi
package data
package notes

final case class Grouped(grouped: List[Note]) derives io.circe.Codec.AsObject
