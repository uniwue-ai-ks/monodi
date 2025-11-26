package de.olyro.monodi
package exprt

trait FontKind derives CanEqual:
  def keepSepcial(f: FontKind): FontKind =
    this match
      case FontKind.Normal => f
      case x               => x
object FontKind:
  case object Normal extends FontKind
  case object Caps   extends FontKind
  case object Italic extends FontKind
