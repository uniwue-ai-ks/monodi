package de.olyro.monodi
package data
package notes

import cats.*
import cats.data.*
import cats.implicits.*

object Modifier:

  def modify[F[_]: Monad](m: Modifier[F], c: RootContainer): F[Option[RootContainer]] =
    (for
      pre      <- OptionT(m.preRoot(c))
      children <- OptionT.liftF(pre.children.traverseFilter(modifyRootChildren(m, _)))
      post     <- OptionT(m.postRoot(pre.copy(children = children)))
    yield post).value

  def modifyRootChildren[F[_]: Monad](m: Modifier[F], c: RootChildren): F[Option[RootChildren]] =
    c match
      case ft: FormteilContainer => modify(m, ft).widen
      case mc: MiscContainer     => modify(m, mc).widen

  def modify[F[_]: Monad](m: Modifier[F], c: MiscContainer): F[Option[MiscContainer]] =
    (for
      pre      <- OptionT(m.preMisc(c))
      children <- OptionT.liftF(pre.children.traverseFilter(modifyMiscChildren(m, _)))
      post     <- OptionT(m.postMisc(pre.copy(children = children)))
    yield post).value

  def modifyMiscChildren[F[_]: Monad](m: Modifier[F], c: MiscChildren): F[Option[MiscChildren]] =
    c match
      case pc: ParatextContainer => modify(m, pc).widen
      case zc: ZeileContainer    => modify(m, zc).widen

  def modify[F[_]: Monad](m: Modifier[F], c: FormteilContainer): F[Option[FormteilContainer]] =
    (for
      pre      <- OptionT(m.preForm(c))
      children <- OptionT.liftF(pre.children.traverseFilter(modifyFormteilChildren(m, _)))
      post     <- OptionT(m.postForm(pre.copy(children = children)))
    yield post).value

  def modifyFormteilChildren[F[_]: Monad](m: Modifier[F], c: FormteilChildren): F[Option[FormteilChildren]] =
    c match
      case pc: ParatextContainer => modify(m, pc).widen
      case zc: ZeileContainer    => modify(m, zc).widen
      case ft: FormteilContainer => modify(m, ft).widen

  def modify[F[_]: Monad](m: Modifier[F], c: ParatextContainer): F[Option[ParatextContainer]] =
    m.prePara(c).flatMap(_.flatTraverse(m.postPara))

  def modify[F[_]: Monad](m: Modifier[F], c: ZeileContainer): F[Option[ZeileContainer]] =
    (for
      pre      <- OptionT(m.preZeile(c))
      children <- OptionT.liftF(pre.children.traverseFilter(modify(m, _)))
      post     <- OptionT(m.postZeile(pre.copy(children = children)))
    yield post).value

  def modify[F[_]: Monad](m: Modifier[F], c: LinePart): F[Option[LinePart]] =
    (for
      pre       <- OptionT(m.preLinePart(c))
      withChild <- OptionT.liftF(pre match {
                     case s: Syllable    => {
                       def grouped(g: Grouped): F[Option[Grouped]] =
                         g.grouped
                           .traverseFilter(modify(m, _))
                           .map({
                             case Nil     => None
                             case x :: xs => Some(Grouped(x :: xs))
                           })

                       def nonSpaced(ns: NonSpaced): F[Option[NonSpaced]] =
                         ns.nonSpaced
                           .traverseFilter(grouped)
                           .map({
                             case Nil     => None
                             case x :: xs => Some(NonSpaced(x :: xs))
                           })

                       def spaced(s: Spaced): F[Spaced] = s.spaced.traverseFilter(nonSpaced).map(nss => Spaced(nss))

                       spaced(s.notes).map(newNotes => s.copy(notes = newNotes))
                     }
                     case l: LineChange  => l.pure[F]
                     case f: FolioChange => f.pure[F]
                     case c: Clef        => c.pure[F]
                     case b: Box         => b.pure[F]
                   })
      post      <- OptionT(m.postLinePart(withChild))
    yield post).value

  def modify[F[_]: Monad](m: Modifier[F], n: Note): F[Option[Note]] =
    m.preNote(n).flatMap(_.flatTraverse(m.postNote))

  trait Modifier[F[_]]:

    /**
      * Since a para doesn't have any children, postPara will be called directly with the result of prePara.
      */
    def prePara(c: ParatextContainer): F[Option[ParatextContainer]]

    /**
      * Since a para doesn't have any children, postPara will be called directly with the result of prePara.
      */
    def postPara(c: ParatextContainer): F[Option[ParatextContainer]]
    def preZeile(c: ZeileContainer): F[Option[ZeileContainer]]
    def postZeile(c: ZeileContainer): F[Option[ZeileContainer]]
    def preForm(c: FormteilContainer): F[Option[FormteilContainer]]
    def postForm(c: FormteilContainer): F[Option[FormteilContainer]]
    def preMisc(c: MiscContainer): F[Option[MiscContainer]]
    def postMisc(c: MiscContainer): F[Option[MiscContainer]]
    def preRoot(c: RootContainer): F[Option[RootContainer]]
    def postRoot(c: RootContainer): F[Option[RootContainer]]
    def preLinePart(c: LinePart): F[Option[LinePart]]
    def postLinePart(c: LinePart): F[Option[LinePart]]

    /**
      * Since a note doesn't have any children, postNote will be called directly with the result of preNote.
      */
    def preNote(c: Note): F[Option[Note]]

    /**
      * Since a note doesn't have any children, postNote will be called directly with the result of preNote.
      */
    def postNote(c: Note): F[Option[Note]]

  trait WithDefaults[F[_]] extends Modifier[F]:
    protected case class Wrapper()(using val monad: Monad[F])
    /** Provide evidence that F: Monad */
    protected val mw: Wrapper

    override def prePara(c: ParatextContainer): F[Option[ParatextContainer]]  = mw.monad.pure(Option(c))
    override def postPara(c: ParatextContainer): F[Option[ParatextContainer]] = mw.monad.pure(Option(c))
    override def preZeile(c: ZeileContainer): F[Option[ZeileContainer]]       = mw.monad.pure(Option(c))
    override def postZeile(c: ZeileContainer): F[Option[ZeileContainer]]      = mw.monad.pure(Option(c))
    override def preForm(c: FormteilContainer): F[Option[FormteilContainer]]  = mw.monad.pure(Option(c))
    override def postForm(c: FormteilContainer): F[Option[FormteilContainer]] = mw.monad.pure(Option(c))
    override def preMisc(c: MiscContainer): F[Option[MiscContainer]]          = mw.monad.pure(Option(c))
    override def postMisc(c: MiscContainer): F[Option[MiscContainer]]         = mw.monad.pure(Option(c))
    override def preRoot(c: RootContainer): F[Option[RootContainer]]          = mw.monad.pure(Option(c))
    override def postRoot(c: RootContainer): F[Option[RootContainer]]         = mw.monad.pure(Option(c))
    override def preLinePart(c: LinePart): F[Option[LinePart]]                = mw.monad.pure(Option(c))
    override def postLinePart(c: LinePart): F[Option[LinePart]]               = mw.monad.pure(Option(c))
    override def preNote(c: Note): F[Option[Note]]                            = mw.monad.pure(Option(c))
    override def postNote(c: Note): F[Option[Note]]                           = mw.monad.pure(Option(c))
