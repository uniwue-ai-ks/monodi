package de.olyro.monodi.exprt.print.markdown

enum MdText:
  case MdNormal(text: String)
  case MdEmph(text: String)
  case MdStrongEmph(text: String)
  case MdImage(altText: Option[String], src: String)
