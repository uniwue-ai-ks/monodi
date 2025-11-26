package de.olyro.monodi
package data
package notes

final case class NonSpaced(nonSpaced: List[Grouped]) derives io.circe.Codec.AsObject
