package de.olyro.monodi.exprt.print.hierarchy

enum Numbering derives CanEqual:
  case Numbers
  case Letters

  def all: LazyList[String] = this match
    case Numbers => LazyList.from(1).map(_.toString)
    case Letters => LazyList.continually('a' to 'z').flatten.map(_.toString)
