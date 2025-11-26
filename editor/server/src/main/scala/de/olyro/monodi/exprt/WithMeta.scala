package de.olyro.monodi
package exprt

import cats.*
import cats.implicits.*

final case class WithMeta[Meta, +Data](
    data: Data,
    meta: Meta,
):
  def mapMeta(f: Meta => Meta): WithMeta[Meta, Data] =
    WithMeta(data, f(meta))

  def mapWithMeta[D2](f: WithMeta[Meta, Data] => D2): WithMeta[Meta, D2] =
    WithMeta(f(this), meta)

  def split[L, R](using ev: <:<[Data, Either[L, R]]): Either[WithMeta[Meta, L], WithMeta[Meta, R]] =
    ev(data) match
      case Left(l)  => Left(WithMeta(l, meta))
      case Right(r) => Right(WithMeta(r, meta))

object WithMeta:
  given withMetaFunctor: [Meta] => Functor[WithMeta[Meta, _]] =
    new Functor[WithMeta[Meta, _]]:
      type F[X] = WithMeta[Meta, X]
      def map[A, B](fa: F[A])(f: A => B): F[B] = fa.copy(data = f(fa.data))

  given withMetaTraverse: [Meta] => Traverse[WithMeta[Meta, _]] =
    new Traverse[WithMeta[Meta, _]]:
      def foldLeft[A, B](fa: WithMeta[Meta, A], b: B)(f: (B, A) => B): B                                               = f(b, fa.data)
      def foldRight[A, B](fa: WithMeta[Meta, A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): cats.Eval[B]                = f(fa.data, lb)
      def traverse[G[_], A, B](fa: WithMeta[Meta, A])(f: A => G[B])(using ev: Applicative[G]): G[WithMeta[Meta, B]] =
        f(fa.data).map(b => fa.copy(data = b))

