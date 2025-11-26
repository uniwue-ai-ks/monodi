package de.olyro.monodi.`exprt`

import cats.data.State
import de.olyro.monodi.data.notes.*
import cats.implicits.*
import de.olyro.monodi.data.notes.Modifier.Modifier

object Pages:

  final case class Page(idx: Int, label: String, notes: RootContainer, imageUri: String)

  final private case class LabelledPageId(uuid: String, label: String)
  final private case class PageBreak(id: LabelledPageId, next: String)

  sealed private trait Phase derives CanEqual
  private case object Seeking                     extends Phase
  private case class Busy(target: LabelledPageId) extends Phase

  private type F[X] = State[(Phase, Vector[PageBreak]), X]

  private val modifier: Modifier[F] = new Modifier.WithDefaults[F]:
    override protected val mw: Wrapper = Wrapper()

    override def prePara(c: ParatextContainer): F[Option[ParatextContainer]] = inspect(c)(pageBreakInParatext)
    override def preZeile(c: ZeileContainer): F[Option[ZeileContainer]]      = inspect(c)(pageBreakInLine)

  private def inspect[C](c: C)(f: C => Option[String])(using uuid: HasUUID[C]): F[Option[C]] =
    State.modify[(Phase, Vector[PageBreak])] { case (p, acc) =>
      (p, f(c)) match
        case (Seeking, Some(label))  => (Busy(LabelledPageId(uuid(c), label)), acc)
        case (Seeking, None)         => (Seeking, acc)
        case (Busy(id), Some(label)) => (Busy(LabelledPageId(uuid(c), label)), acc :+ PageBreak(id, uuid(c)))
        case (Busy(id), None)        => (Seeking, acc :+ PageBreak(id, uuid(c)))
    } *> State.pure(Some(c))

  private def pageBreakInParatext(p: ParatextContainer): Option[String] =
    val pattern = """(?s).*\(([fp].*)\)""".r
    p.text.trim match
      case pattern(label) => Some(label)
      case _              => None

  private def pageBreakInLine(p: ZeileContainer): Option[String] = p.children.collectFirst:
    case FolioChange(_, _, _, l) => l

  trait HasUUID[T]:
    def apply(t: T): String
  given [C <: Container] => HasUUID[C] = c => c.uuid
  //given HasUUID[LinePart] = lp => lp.uuid

  def mkPages(rc: RootContainer, startLabel: String): List[Either[String, Page]] =
    val (_, pageBreaks) = Modifier.modify(modifier, rc).runS((Seeking, Vector.empty)).value

    if pageBreaks.isEmpty then
      List(Right(Page(0, startLabel, rc, "")))
    else
      val tmp =
        PageBreak(LabelledPageId("", startLabel), rc.uuid) +: pageBreaks :+ PageBreak(LabelledPageId(rc.uuid, ""), "")
      ((for
        // tmp has at lest 2 elements so sliding(2) will produce only 2-element vectors
        (Vector(start, end), idx) <- tmp.sliding(2).zipWithIndex
      yield {
        Cutter
          .cut(rc, start.next, end.id.uuid)
          .leftMap(_ => s"failed to split between $start and $end")
          .map(_.map(Page(idx, start.id.label, _, "")))
      })).map {
        case Right(Some(page)) => Right(page)
        case Right(None)       => Left("ignored empty page")
        case Left(msg)         => Left(msg)
      }.toList
