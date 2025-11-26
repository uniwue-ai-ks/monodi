package de.olyro.monodi
package exprt
package print
package cover

enum Cover derives CanEqual:
  case TextCover(
      topItems: List[LineItem],
      bottomItems: List[LineItem]
  )
