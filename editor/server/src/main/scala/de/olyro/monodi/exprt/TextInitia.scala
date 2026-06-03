package de.olyro.monodi.exprt

import de.olyro.monodi.data.notes.*
import de.olyro.monodi.Util.splitWhen

/**
  * A helper object to gather multiple textinitia from a single document.
  *
  * The textinitia are gathered by taking the first 3 words after each signature change.
  */
object TextInitia:
  def getTextInitia(notes: RootContainer): List[String] =
    val items                  = getSignaturesChangesAndTexts(notes)
    val mergedItems            = mergeConsecutiveItemsOfSameType(items)
    val splitAtSignatureChange = mergedItems.splitWhen(_ == Item.SignatureChange)
    splitAtSignatureChange.map(_.flatMap(_.normalWords).take(3).mkString(" ")).filter(s => !s.isBlank).toList

  private def hasSignature(fc: FormteilContainer): Boolean =
    fc.data.exists: data =>
      data.name == FormteilData.Signatur && !data.data.isBlank()

  enum Item derives CanEqual:
    case SignatureChange

    /** Those are text from syllables in the notes, like List("Lo-", "rem", "ip-", "sum") */
    case Syllables(syllables: Vector[String])

    def nonEmpty: Boolean =
      this match
        case Syllables(syllables) => syllables.nonEmpty
        case SignatureChange      => true

    def normalWords: Vector[String] =
      this match
        case Syllables(syllables) =>
          syllables.mkString(" ").replaceAll("- ", "").split("\\s+").toVector.filter(s => !s.isBlank)
        case SignatureChange      => Vector.empty

  private def getSignaturesChangesAndTexts(c: Container): Vector[Item] =
    def fromFormteil(fc: FormteilContainer): Vector[Item] =
      if hasSignature(fc) then Vector(Item.SignatureChange) else Vector.empty

    def fromZeile(zc: ZeileContainer): Vector[Item] =
      val syllables = zc.children.toVector
        .collect:
          case s: Syllable => s.text
        .map(_.trim)
        .filter(s => !s.isBlank)

      Vector(Item.Syllables(syllables)).filter(_.nonEmpty)

    def extract(c: Container): Vector[Item] =
      c match
        case zc: ZeileContainer    => fromZeile(zc)
        case fc: FormteilContainer => fromFormteil(fc)
        case _: ParatextContainer  => Vector.empty
        case _: MiscContainer      => Vector.empty
        case _: RootContainer      => Vector.empty

    Container.foldMany(extract)(c)

  private def mergeConsecutiveItemsOfSameType(texts: Vector[Item]): Vector[Item] =
    texts.foldLeft(Vector.empty[Item]):
      case (acc, Item.SignatureChange) => acc :+ Item.SignatureChange

      case (acc, Item.Syllables(syllables)) =>
        acc.lastOption match
          case None                                => acc :+ Item.Syllables(syllables)
          case Some(Item.SignatureChange)          => acc :+ Item.Syllables(syllables)
          case Some(Item.Syllables(prevSyllables)) => acc.init :+ Item.Syllables(prevSyllables ++ syllables)
