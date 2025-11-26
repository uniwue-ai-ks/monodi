package de.olyro.monodi
package exprt

import data.notes.*
import cats.data.NonEmptyList
import scala.collection.immutable.Nil

object ViewModel:
  type Data = Either[ParatextContainer, NonEmptyList[LinePart]]

  def getLineMetadata(r: RootContainer): List[WithMeta[Line, Data]] =
    def rec(c: Container): List[WithMeta[Line, Data]] = c match
      case pc: ParatextContainer => List(WithMeta(Left(pc), Line(Nil, FontKind.Normal, Nil, pc.text, Nil)))
      case z: ZeileContainer     =>
        z.children match
          case Nil          => Nil
          case head :: tail =>
            List(
              WithMeta(
                Right(NonEmptyList(head, tail)),
                Line(
                  Nil,
                  FontKind.Normal,
                  Nil,
                  z.children.flatMap(getLinePartText).mkString(" "),
                  r.comments,
                ),
              ),
            )
      case f: FormteilContainer  =>
        val metas = f.children.zipWithIndex.map({ case (child, i) =>
          rec(child).map(_.mapMeta(l => l.copy(hierarchy = i :: l.hierarchy)))
        })

        val withSig = f.data.find(d => d.name == FormteilData.Signatur) match
          case None                       => metas.flatten
          case Some(FormteilData(_, sig)) =>
            metas.flatten.map(_.mapMeta(x => x.copy(signatures = sig :: x.signatures)))

        withSig.map(_.mapMeta(old => old.copy(font = old.font.keepSepcial(getFontKind(f)))))

      case c: RootContainer =>
        c.children.zipWithIndex.flatMap { case (cc, i) =>
          rec(cc).map(_.mapMeta(line => line.copy(hierarchy = i :: line.hierarchy)))
        }
      case c: MiscContainer =>
        c.children.zipWithIndex.flatMap { case (cc, i) =>
          rec(cc).map(_.mapMeta(line => line.copy(hierarchy = i :: line.hierarchy)))
        }
    rec(r)

  def getCommentData(r: RootContainer): List[CommentData] =
    val comments = r.comments.groupBy(_.startUUID)

    def forParatextContainer(pc: ParatextContainer): List[CommentData] =
      pc.comment.toList.map(c => CommentData(Left(c), pc, Nil))

    def forZeileContainer(zc: ZeileContainer): List[CommentData] =
      Container.getIds(zc).flatMap(id => comments.getOrElse(id, Nil).map(c => CommentData(Right(c), zc, Nil)))

    def forFormteilContainer(fc: FormteilContainer, subs: List[List[CommentData]]): List[CommentData] =
      val sigs = fc.data.filter(_.name == FormteilData.Signatur).map(_.data)
      sigs.foldLeft(subs.flatten)((acc, sig) => acc.map(_.prependSignature(sig)))

    Container
      .recursiveCata[List[CommentData]](
        forParatextContainer,
        forZeileContainer,
        forFormteilContainer,
        (_, subs) => subs.flatten,
        (_, subs) => subs.flatten,
      )(r)

  def getLinePartText(lp: LinePart): Option[String] =
    lp match
      case x: Syllable    => Some(x.text)
      case _: LineChange  => None
      case x: FolioChange => Some(x.text)
      case _: Clef        => None
      case _: Box         => None

  def getFontKind(f: FormteilContainer): FontKind =
    if usesCaps(f) then FontKind.Caps
    else if usesItalics(f) then FontKind.Italic
    else FontKind.Normal

  def usesCaps(f: FormteilContainer) =
    f.data
      .find(d => d.name == FormteilData.Status)
      .fold(false)(fd => fd.data == "Einsatzmarke")

  def usesItalics(f: FormteilContainer) =
    f.data
      .find(d => d.name == FormteilData.Status)
      .fold(false)(fd => fd.data == "Refrain")

  def getCommentInfo(linePart: LinePart, comments: List[Comment]): (Boolean, Boolean, String) =
    val (starts, ends) = (comments.map(_.startUUID), comments.map(_.endUUID))

    val ids = linePart match
      case s @ Syllable(uuid, _, _, kind) =>
        if kind == SyllableType.Normal then uuid :: s.getAllNotes.map(_.uuid)
        else Nil
      case LineChange(uuid, _, _)         => List(uuid)
      case FolioChange(uuid, _, _, _)     => List(uuid)
      case Clef(uuid, _, _, _, _)         => List(uuid)
      case Box(uuid, _)          => List(uuid)

    val commentId = comments
      .find(c => ids.contains(c.startUUID) || ids.contains(c.endUUID))
      .map(_.concatenatedIds)
      .getOrElse("")

    (ids.exists(starts.contains), ids.exists(ends.contains), commentId)
