package de.olyro.monodi
package exprt

import de.olyro.monodi.data.Document
import java.nio.file.Path
import de.olyro.monodi.exprt.Message.FromDoc
import de.olyro.monodi.exprt.Message.FromSource
import de.olyro.monodi.exprt.Message.NoCustomDir
import de.olyro.monodi.exprt.Message.FromCustomFile

final case class Message(severity: Message.Severity, source: Message.Source, text: String):
  def print: String =
    val s = source match
      case FromDoc(document)    => document.dokumenten_id + " / " + document.id
      case FromSource(source)   => source.quellensigle + " / " + source.id
      case NoCustomDir          => "NoCustomDir"
      case FromCustomFile(path) => s"CustomFile: ${path.toString}"

    "%20s %60s %s".format(severity, s, text)

object Message:
  sealed trait Source derives CanEqual
  final case class FromDoc(document: Document)                     extends Source
  final case class FromSource(source: de.olyro.monodi.data.Source) extends Source
  case object NoCustomDir                                          extends Source
  final case class FromCustomFile(path: Path)                      extends Source

  sealed trait Severity derives CanEqual
  case object Warning extends Severity
  case object Error   extends Severity

  object Severity:
    given SeverityOrdering: Ordering[Severity]:

      override def compare(x: Severity, y: Severity): Int = (x, y) match
        case (Warning, Warning) => 0
        case (Warning, Error)   => -1
        case (Error, Warning)   => 1
        case (Error, Error)     => 0
