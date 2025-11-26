package de.olyro.monodi.exprt.print.markdown

enum MdBlock derives CanEqual:
  case MdParagraph(texts: List[MdText])
  case MdHeading(level: Int, texts: List[MdText])
  case MdList(ordered: Boolean, items: List[List[MdText]])
