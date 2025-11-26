package de.olyro.monodi
package data
package notes

import cats.implicits.*
import io.circe.*, io.circe.parser.*

sealed trait Container derives io.circe.derivation.ConfiguredCodec:
  def uuid: String

sealed trait RootChildren extends Container derives io.circe.derivation.ConfiguredCodec
sealed trait FormteilChildren extends Container derives io.circe.derivation.ConfiguredCodec
sealed trait MiscChildren     extends FormteilChildren derives io.circe.derivation.ConfiguredCodec

final case class RootContainer(
    uuid: String,
    children: List[RootChildren],
    comments: List[Comment],
    documentType: DocumentType,
    version: Option[Int],
    globalComment: Option[CommentTree],
) extends Container

final case class FormteilContainer(uuid: String, children: List[FormteilChildren], data: List[FormteilData])
    extends Container
    with RootChildren
    with FormteilChildren

final case class ZeileContainer(uuid: String, children: List[LinePart])
    extends Container
    with FormteilChildren
    with MiscChildren

final case class ParatextContainer(
    uuid: String,
    text: String,
    retro: Boolean,
    paratextType: ParatextType,
    comment: Option[ParatextComment],
) extends Container
    with FormteilChildren
    with MiscChildren

final case class MiscContainer(uuid: String, children: List[MiscChildren]) extends Container with RootChildren

object Container:
  def fold[A](f: Container => A)(c: Container): List[A] =
    c match
      case pc: ParatextContainer => List(f(pc))
      case zc: ZeileContainer    => List(f(zc))
      case fc: FormteilContainer => f(fc) :: fc.children.flatMap(fold(f))
      case mc: MiscContainer     => f(mc) :: mc.children.flatMap(fold(f))
      case rc: RootContainer     => f(rc) :: rc.children.flatMap(fold(f))

  def flatCata[A](f: Container => A)(c: Container): A =
    c match
      case pc: ParatextContainer => f(pc)
      case zc: ZeileContainer    => f(zc)
      case fc: FormteilContainer => f(fc)
      case mc: MiscContainer     => f(mc)
      case rc: RootContainer     => f(rc)

  def recursiveCata[A](
      para: ParatextContainer => A,
      zeile: ZeileContainer => A,
      form: (FormteilContainer, List[A]) => A,
      misc: (MiscContainer, List[A]) => A,
      root: (RootContainer, List[A]) => A,
  )(c: Container): A =
    c match
      case pc: ParatextContainer => para(pc)
      case zc: ZeileContainer    => zeile(zc)
      case fc: FormteilContainer =>
        form(
          fc,
          fc.children.map(recursiveCata(para, zeile, form, misc, root)),
        )
      case mc: MiscContainer     =>
        misc(mc, mc.children.map(recursiveCata(para, zeile, form, misc, root)))
      case rc: RootContainer     =>
        root(rc, rc.children.map(recursiveCata(para, zeile, form, misc, root)))

  def getIds(c: Container): List[String] =
    fold({
      case pc: ParatextContainer => List(pc.uuid)
      case zc: ZeileContainer    => zc.uuid :: zc.children.flatMap(getIds)
      case fc: FormteilContainer => List(fc.uuid)
      case mc: MiscContainer     => List(mc.uuid)
      case rc: RootContainer     => List(rc.uuid)
    })(c).flatten

  def getIds(lp: LinePart): List[String] =
    lp match
      case s: Syllable    => s.uuid :: s.notes.spaced.flatMap(_.nonSpaced.flatMap(_.grouped.map(_.uuid)))
      case l: LineChange  => List(l.uuid)
      case f: FolioChange => List(f.uuid)
      case c: Clef        => List(c.uuid)
      case b: Box         => List(b.uuid)

  def liftZeileContainer(z: ZeileContainer): RootContainer =
    RootContainer(
      "empty-dummy",
      List(FormteilContainer("empty-dummy-f", List(z), Nil)),
      Nil,
      DocumentType.Level1,
      None,
      None,
    )

  def getAllNotes(c: Container): List[Note] =
    Container
      .fold[List[Note]] {
        case _: RootContainer            => Nil
        case _: FormteilContainer        => Nil
        case _: MiscContainer            => Nil
        case _: ParatextContainer        => Nil
        case ZeileContainer(_, children) => children.flatMap(LinePart.getAllNotes)
      }(c)
      .flatten

  def parse(in: String): Either[String, (RootContainer, Evolutions.EvolutionResult)] =
    (for
      json        <- decode[Json](in)
      transformed <- Evolutions.applyAllJsonEvolutions(json).leftMap(new RuntimeException(_))
    yield transformed).leftMap(_.getMessage)

  def toLineParts(rc: RootContainer): List[LinePart] =
    def toLineParts(fc: FormteilContainer): List[LinePart] = fc.children flatMap:
      case ZeileContainer(_, children) => children
      case fc: FormteilContainer       => toLineParts(fc)
      case _: ParatextContainer        => Nil

    rc.children flatMap:
      case fc: FormteilContainer => toLineParts(fc)
      case _                     => Nil

