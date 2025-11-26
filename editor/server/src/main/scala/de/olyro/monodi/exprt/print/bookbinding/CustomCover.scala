package de.olyro.monodi
package exprt
package print
package bookbinding

enum CustomCover derives io.circe.derivation.ConfiguredCodec:
  case LineItemCover(items: List[LineItem])
