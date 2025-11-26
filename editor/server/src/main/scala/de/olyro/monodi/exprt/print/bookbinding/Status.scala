package de.olyro.monodi
package exprt.print
package bookbinding

import de.olyro.monodi.data.BinaryData

enum Status derives io.circe.derivation.ConfiguredCodec:
  case Queued(atIndex: Int)
  case Active(progress: Progress)
  case Done(data: BinaryData)
  case NotFound
