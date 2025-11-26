package de.olyro.monodi
package exprt

import cats.*
import cats.implicits.*
import zio.ZIO

final case class Accu[+A](
    log: Vector[Message],
    notes: Vector[DrawnNotes],
    data: Vector[A]
):
  def addData[AA >: A](a: AA): Accu[AA]                        = copy(data = data :+ a)
  def logError(s: Message.Source, e: String): Accu[A]          = copy(log = log :+ Message(Message.Error, s, e))
  def logWarning(s: Message.Source, w: String): Accu[A]        = copy(log = log :+ Message(Message.Warning, s, w))
  def logWarnings(s: Message.Source, w: List[String]): Accu[A] = w.foldLeft(this)((a, l) => a.logWarning(s, l))
  def addNote(n: DrawnNotes): Accu[A]                          = copy(notes = notes :+ n)
  def dataFromNote[AA >: A](f: DrawnNotes => AA): Accu[AA]     = copy(data = data ++ notes.map(f))
  def combineData[B](f: Vector[A] => B): Accu[B]               = copy(data = Vector(f(data)))

object Accu:
  def data[A](a: A): Accu[A]                               = Accu(Vector.empty, Vector.empty, Vector(a))
  def allData[A](as: Iterable[A]): Accu[A]                 = Accu(Vector.empty, Vector.empty, as.toVector)
  def note[A](n: DrawnNotes): Accu[A]                      = Accu(Vector.empty, Vector(n), Vector.empty)
  def logError[A](s: Message.Source, e: String): Accu[A]   = empty.logError(s, e)
  def logWarning[A](s: Message.Source, w: String): Accu[A] = empty.logWarning(s, w)
  def empty[A]: Accu[A]                                    = Accu(Vector.empty, Vector.empty, Vector.empty)

  given makeMonoidK: MonoidK[Accu] = new MonoidK[Accu]:
    def combineK[A](x: Accu[A], y: Accu[A]): Accu[A] = Accu(
      x.log ++ y.log,
      x.notes ++ y.notes,
      x.data ++ y.data
    )

    def empty[A]: Accu[A] = Accu(Vector.empty, Vector.empty, Vector.empty)

  given makeMonoid: [A] => Monoid[Accu[A]] = makeMonoidK.algebra

  given traverse: Traverse[Accu] = new Traverse[Accu]:
    override def foldLeft[A, B](fa: Accu[A], b: B)(f: (B, A) => B): B                           = fa.data.foldLeft(b)(f)
    override def foldRight[A, B](fa: Accu[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      Traverse[Vector].foldRight(fa.data, lb)(f)
    override def traverse[G[_]: Applicative, A, B](fa: Accu[A])(f: A => G[B]): G[Accu[B]]       =
      Traverse[Vector].traverse(fa.data)(f).map(result => fa.copy(data = result))

  extension[R, E, A] (zio: ZIO[R, E, Accu[A]])
    def ||>[R1 <: R, E1 >: E](other: ZIO[R1, E1, Accu[A]]): ZIO[R1, E1, Accu[A]] = zio.zipWithPar(other)(_ |+| _)
    def |>[R1 <: R, E1 >: E](other: ZIO[R1, E1, Accu[A]]): ZIO[R1, E1, Accu[A]]  = zio.zipWith(other)(_ |+| _)
