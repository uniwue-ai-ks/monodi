package de.olyro.monodi.exprt.comment_tree

import cats.data.*
import cats.implicits.*
import de.olyro.monodi.data.notes.LinePart
import de.olyro.monodi.data.notes.Container
import de.olyro.monodi.data.notes.Syllable

object ContextFinder:
  type F[A] = StateT[Either[String, *], List[LinePart], A]

  def find(lineParts: List[LinePart], startId: String, endId: String): Either[String, Result] =
    findF(startId, endId).runA(lineParts)

  final case class Result(
      prefix: List[LinePart],
      suffix: List[LinePart],
  )

  private def findF(startId: String, endId: String): F[Result] =
    for
      prefix <- takePrefix(startId)
      middle <- takeIncludingEnd(endId)
      end     = middle.last
      suffix <- linePartType(end) match
                  case NonEndSyllable => takeSuffix
                  case _              => Nil.pure[F]
    yield Result(prefix, suffix)

  private def takePrefix(startId: String): F[List[LinePart]] =
    def go(collecting: Boolean, prefix: List[LinePart]): F[List[LinePart]] =
      peakHead.flatMap({
        case None       => error("startId not found")
        case Some(head) =>
          val isStart = Container.getIds(head).contains(startId)
          val lpt     = linePartType(head)

          if isStart then prefix.reverse.pure[F]
          else if keepCollecting(collecting, lpt) then popHead *> go(true, head :: prefix)
          else popHead *> go(false, Nil)
      })

    go(false, Nil)

  private def keepCollecting(isCollectig: Boolean, linePartType: LinePartType): Boolean =
    (isCollectig, linePartType) match
      case (false, NotASyllable)   => false
      case (false, EndSyllable)    => false
      case (false, NonEndSyllable) => true
      case (true, NotASyllable)    => true
      case (true, EndSyllable)     => false
      case (true, NonEndSyllable)  => true

  sealed trait LinePartType derives CanEqual
  case object NotASyllable   extends LinePartType
  case object EndSyllable    extends LinePartType
  case object NonEndSyllable extends LinePartType

  private def error[A](s: String): F[A]       = s.raiseError[F, A]
  private def get: F[List[LinePart]]          = StateT.get[Either[String, *], List[LinePart]]
  private def set(l: List[LinePart]): F[Unit] = StateT.set[Either[String, *], List[LinePart]](l)

  private def popHead: F[LinePart] = get.flatMap({
    case Nil          => error("popHead: empty list")
    case head :: tail => set(tail).as(head)
  })

  private val peakHead: F[Option[LinePart]] = get.map(_.headOption)

  private def takeIncludingEnd(endId: String): F[NonEmptyList[LinePart]] =
    def go(middle: List[LinePart]): F[NonEmptyList[LinePart]] =
      popHead.flatMap(head =>
        if Container.getIds(head).contains(endId) then (NonEmptyList(head, middle)).reverse.pure[F]
        else go(head :: middle),
      )

    go(Nil)

  private def takeSuffix: F[List[LinePart]] =
    def go(accu: List[LinePart]): F[List[LinePart]] =
      peakHead.flatMap({
        case None       => accu.reverse.pure[F]
        case Some(head) =>
          linePartType(head) match {
            case NotASyllable   => popHead *> go(head :: accu)
            case EndSyllable    => (head :: accu).reverse.pure[F]
            case NonEndSyllable => popHead *> go(head :: accu)
          }
      })

    go(Nil)

  private def linePartType(lp: LinePart): LinePartType =
    lp match
      case Syllable(_, text, _, _) => if text.endsWith("-") then NonEndSyllable else EndSyllable
      case _                       => NotASyllable
