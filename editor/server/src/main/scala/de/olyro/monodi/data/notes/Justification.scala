package de.olyro.monodi
package data
package notes

sealed trait Justification derives io.circe.derivation.ConfiguredCodec, CanEqual
object Justification:
  case object Left   extends Justification
  case object Center extends Justification
  case object Right  extends Justification
