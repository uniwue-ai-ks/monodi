package de.olyro.monodi
package data
package notes

final case class Spaced(spaced: List[NonSpaced]) derives io.circe.Codec.AsObject
