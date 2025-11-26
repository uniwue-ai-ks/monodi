package de.olyro.monodi.viewer

import de.olyro.monodi.data.notes.*

object Filter:

  def filter(rc: RootContainer, filter: String): RootContainer =
    val matcher = Matcher.make(filter)

    def getRelevantChildren(
        parentSignature: String,
        fc: FormteilContainer,
    ): Vector[FormteilContainer] =
      val signature     = parentSignature + fc.data.filter(_.name == FormteilData.Signatur).map(_.data).mkString
      val withSignature =
        if signature.isEmpty then fc
        else
          fc.copy(data =
            fc.data.filter(_.name != FormteilData.Signatur) :+ FormteilData(FormteilData.Signatur, signature),
          )

      getChildren(withSignature) match
        case Empty           => Vector.empty
        case InvalidChildren => Vector.empty

        case ParatextChildren(children) =>
          if matcher.matchesSignature(signature) || matcher.matchesText(children.map(_.text).mkString(" ")) then
            Vector(withSignature)
          else Vector.empty

        case LeafChildren(children) =>
          lazy val text = children
            .map({
              case Left(p)  => p.text
              case Right(z) =>
                z.children
                  .map(_ match {
                    case Syllable(_, text, _, _)    => text
                    case LineChange(_, _, _)        => ""
                    case FolioChange(_, _, _, text) => text
                    case Clef(_, _, _, _, _)        => ""
                    case Box(_, _) => ""
                  })
                  .mkString(" ")
            })
            .mkString(" ")

          if matcher.matchesSignature(signature) || matcher.matchesText(text) then Vector(withSignature)
          else Vector.empty

        case MiddleChildren(children) =>
          children.flatMap({
            case Left(p)   =>
              if matcher.matchesText(p.text) then Vector(withSignature.copy(children = List(p))) else Vector.empty
            case Right(fc) => getRelevantChildren(signature, fc)
          })

    rc.copy(children =
      rc.children
        .collect({ case fc: FormteilContainer => getRelevantChildren("", fc) })
        .flatten,
    )


  private def getChildren(fc: FormteilContainer): ChildrenList =
    fc.children.foldLeft(Empty: ChildrenList)((accu, next) =>
      (accu, next) match {
        case (InvalidChildren, _) => InvalidChildren

        case (Empty, c: ParatextContainer) => ParatextChildren(Vector(c))
        case (Empty, c: FormteilContainer) => MiddleChildren(Vector(Right(c)))
        case (Empty, c: ZeileContainer)    => LeafChildren(Vector(Right(c)))

        case (list: ParatextChildren, c: ParatextContainer) => ParatextChildren(list.children :+ c)
        case (list: ParatextChildren, c: FormteilContainer) => MiddleChildren(list.children.map(Left(_)) :+ Right(c))
        case (list: ParatextChildren, c: ZeileContainer)    => LeafChildren(list.children.map(Left(_)) :+ Right(c))

        case (list: LeafChildren, c: ParatextContainer) => LeafChildren(list.children :+ Left(c))
        case (_: LeafChildren, _: FormteilContainer)    => InvalidChildren
        case (list: LeafChildren, c: ZeileContainer)    => LeafChildren(list.children :+ Right(c))

        case (list: MiddleChildren, c: ParatextContainer) => MiddleChildren(list.children :+ Left(c))
        case (list: MiddleChildren, c: FormteilContainer) => MiddleChildren(list.children :+ Right(c))
        case (_: MiddleChildren, _: ZeileContainer)       => InvalidChildren
      },
    )

  sealed private trait ChildrenList derives CanEqual
  private case object InvalidChildren                                                               extends ChildrenList
  private case object Empty                                                                         extends ChildrenList
  private case class ParatextChildren(children: Vector[ParatextContainer])                          extends ChildrenList
  private case class LeafChildren(children: Vector[Either[ParatextContainer, ZeileContainer]])      extends ChildrenList
  private case class MiddleChildren(children: Vector[Either[ParatextContainer, FormteilContainer]]) extends ChildrenList

  final case class Matcher(
      matchesSignature: String => Boolean,
      matchesText: String => Boolean,
  )

  object Matcher:
    def make(filter: String): Matcher =
      val normalized = normalize(filter)

      if normalized.contains(";") then
        val possibleSignatures = normalized.split(";").filter(_.nonEmpty)
        Matcher(
          s => possibleSignatures.contains(normalize(s)),
          _ => false,
        )
      else
        val simple = (s: String) => normalize(s).contains(normalized)
        Matcher(
          simple,
          simple,
        )

    private def normalize(s: String): String =
      s
        .toLowerCase()
        .replaceAll("\\s", "")
        .replace("-", "")

