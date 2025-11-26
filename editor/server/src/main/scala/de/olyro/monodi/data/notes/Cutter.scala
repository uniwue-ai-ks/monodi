package de.olyro.monodi
package data
package notes

import cats.data.*, cats.implicits.*
import cats.mtl.*

import Modifier.*

object Cutter:
  private type F[X] = EitherT[State[Phase, _], Unit, X]
  private val FR = implicitly[Raise[F, Unit]]
  private val MS = implicitly[Stateful[F, Phase]]

  def cut(root: RootContainer, start: String, end: String): Either[Unit, Option[RootContainer]] =
    Modifier.modify[F](modifier(start, end), root).value.runA(BeforeStart).value

  private def modifier(start: String, end: String): Modifier[F] = new Modifier[F]:
    def postRoot(c: RootContainer): F[Option[RootContainer]] = defaultPost(c)
    def preRoot(c: RootContainer): F[Option[RootContainer]]  = defaultPre(c)

    def postForm(c: FormteilContainer): F[Option[FormteilContainer]] = defaultPost(c)
    def preForm(c: FormteilContainer): F[Option[FormteilContainer]]  = defaultPre(c)

    def preMisc(c: MiscContainer): F[Option[MiscContainer]]  = defaultPre(c)
    def postMisc(c: MiscContainer): F[Option[MiscContainer]] = defaultPost(c)

    def prePara(c: ParatextContainer): F[Option[ParatextContainer]]  = defaultPre(c)
    def postPara(c: ParatextContainer): F[Option[ParatextContainer]] = defaultPost(c)

    def preZeile(c: ZeileContainer): F[Option[ZeileContainer]]  = defaultPre(c)
    def postZeile(c: ZeileContainer): F[Option[ZeileContainer]] = defaultPost(c)

    def preLinePart(c: LinePart): F[Option[LinePart]]  = defaultPre(c)
    def postLinePart(c: LinePart): F[Option[LinePart]] = defaultPost(c)

    def preNote(c: Note): F[Option[Note]]  = defaultPre(c) *> keep(c)
    def postNote(c: Note): F[Option[Note]] = defaultPost(c) *> keep(c)

    def defaultPost[C](c: C)(using u: Uuid[C]): F[Option[C]] =
      get.flatMap(phase =>
        (matchesEnd(c), phase) match {
          case (true, BeforeStart)         => dieWithInvalidState
          case (true, BetweenStartAndEnd)  => set(AfterEnd) *> keep(c)
          case (true, AfterEnd)            => keep(c)
          case (false, BeforeStart)        => quit
          case (false, BetweenStartAndEnd) => keep(c)
          case (false, AfterEnd)           => keep(c)
        },
      )

    def defaultPre[C](c: C)(using u: Uuid[C]): F[Option[C]] =
      get.flatMap(phase =>
        (matchesStart(c), phase) match {
          case (true, BeforeStart)         => set(BetweenStartAndEnd) *> keep(c)
          case (true, BetweenStartAndEnd)  => dieWithInvalidState
          case (true, AfterEnd)            => dieWithInvalidState
          case (false, BeforeStart)        => keep(c)
          case (false, BetweenStartAndEnd) => keep(c)
          case (false, AfterEnd)           => quit
        },
      )

    def keep[A](a: A): F[Option[A]]  = (Some(a): Option[A]).pure[F]
    def get: F[Phase]                = MS.get
    def set(p: Phase): F[Unit]       = MS.set(p)
    def quit[A]: F[Option[A]]        = (None: Option[A]).pure[F]
    def dieWithInvalidState[A]: F[A] = FR.raise(())

    def matchesStart[X](x: X)(using u: Uuid[X]): Boolean =
      u.id(x) == start

    def matchesEnd[X](x: X)(using u: Uuid[X]): Boolean =
      u.id(x) == end


  sealed private trait Markers derives CanEqual
  private case object Zero extends Markers
  private case object One  extends Markers
  private case object Two  extends Markers

  sealed trait Phase derives CanEqual
  case object BeforeStart        extends Phase
  case object BetweenStartAndEnd extends Phase
  case object AfterEnd           extends Phase

  private trait Uuid[A]:
    def id(a: A): String
  private given containerUuid: [C] => (ev: C <:< Container) => Uuid[C] = new Uuid[C]:
    def id(c: C): String = ev(c).uuid

  private given linePartUuid: Uuid[LinePart]                            = new Uuid[LinePart] { def id(lp: LinePart): String = lp.uuid }
  private given noteUuid: Uuid[Note]                                    = new Uuid[Note] { def id(n: Note): String = n.uuid }
