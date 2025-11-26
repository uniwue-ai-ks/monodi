package de.olyro.monodi
package exprt

import cats.data.*

import java.net.URI

object Turtle:
  sealed trait Turtle
  sealed trait TObject
  final case class Ref(uri: URI) extends TObject

  sealed trait Literal                      extends TObject
  final case class LInt(value: Int)         extends Literal
  final case class LBoolean(value: Boolean) extends Literal
  final case class LString(value: String, lang: Option[String] = None)   extends Literal

  final case class Statement(subject: Ref, partials: NonEmptyList[(Ref, TObject)]) extends Turtle
  final case class Blank(partials: (Ref, TObject)*)                                extends TObject
  final case class Raw(value: String)                                              extends Turtle

  sealed abstract case class Prefix(value: String)

  object Statement:
    def apply(subject: Ref, first: (Ref, TObject), rest: (Ref, TObject)*): Statement =
      Statement(subject, NonEmptyList(first, rest.toList))

  def stringify(t: Turtle): String =
    t match
      case s: Statement => stringifyStatement(s)
      case Raw(value)   => value

  def stringifyStatement(s: Statement): String =
    if s.partials.tail.isEmpty then
      s"<${s.subject.uri.toString}> " + stringifyPartial(s.partials.head._1, s.partials.head._2, 0) + "."
    else
      val lastIndex = s.partials.size - 1

      s"<${s.subject.uri.toString}> " +
        s.partials.toList.zipWithIndex
          .map({
            case (partial, index) =>
              (if index != 0 then "    " else "") +
                stringifyPartial(partial._1, partial._2, 4) + (if index == lastIndex then "." else ";")
          })
          .mkString("\n")

  def stringifyLiteral(l: Literal): String =
    l match
      case LInt(v)     => v.toString
      case LBoolean(v) => v.toString
      case LString(v, lang)  => "\"\"\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"\"\"" + lang.fold("")(lang => "@"+lang)

  def stringifyBlank(blank: Blank, indent: Int): String =
    val indentLow  = new String(Array.fill(indent)(' '))
    val indentDeep = new String(Array.fill(indent + 4)(' '))
    val lines      = "[" :: (blank.partials.toList.map(t =>
      indentDeep + stringifyPartial(t._1, t._2, indent + 4) + ";",
    ) :+ (indentLow + "]"))

    lines.mkString("\n")

  def stringifyPartial(ref: Ref, obj: TObject, indent: Int): String =
    obj match
      case Ref(uri2)  => s"<${ref.uri.toString}> <${uri2.toString}>"
      case l: Literal => s"<${ref.uri.toString}> ${stringifyLiteral(l)}"
      case b: Blank   => s"<${ref.uri.toString}> ${stringifyBlank(b, indent)}"
