package de.olyro.monodi
package data
package notes

sealed trait CommentType

object CommentType:
  case object Festtag extends CommentType
  case object Feier extends CommentType
  case object Gesang extends CommentType
  case object Formteil extends CommentType
  case object Aufführung extends CommentType


