package de.olyro.monodi.exprt.comment_tree

import de.olyro.monodi.data.notes.*
import BracketInfo.*
import de.olyro.monodi.exprt.BoundingBox

sealed trait PartiallyDrawn:
  import PartiallyDrawn.*

  def justification: Option[Justification]

  def width: Double = this match
    case UndrawnBracket(_)  => bracketWidth
    case DrawnNotes(_, bb)  => bb.width
    case DrawnText(_, bb)   => bb.width
    case DrawnOpaque(_, bb) => bb.width

  lazy val bracketInfo: BracketInfo = this match
    case UndrawnBracket(_) => BracketInfo.default

    case PartiallyDrawn.DrawnNotes(_, bb) =>
      // I'm not sure if it can happend that the line markers are missing
      // but best not crash here. We just use 0 and 1 to get a minimal
      // bracket height, which will obviously signal that there is some
      // problem, but want invalidate the rest/crash the program.
      val topStaffLine    = bb.resolve("line-marker-38").headOption.map(_.y).getOrElse(0.0)
      val bottomStaffLine = bb.resolve("line-marker-30").headOption.map(_.y).getOrElse(1.0)

      val height = bottomStaffLine - topStaffLine
      BracketInfo(
        priority = 5,
        bracketHeight = height,
        bracketInnerTopToBaseline = 0.75 * height,
      )

    case DrawnText(_, bb) =>
      val baseline = bb.resolve("baseline").headOption.map(_.y).getOrElse(sys.error("baseline not found"))
      val ascent   = bb.resolve("ascent").map(_.y).minOption.getOrElse(sys.error("ascent not found"))
      val descent  = bb.resolve("descent").map(_.y).maxOption.getOrElse(sys.error("descent not found"))
      val height   = descent - ascent

      BracketInfo(
        priority = 4,
        bracketHeight = height,
        bracketInnerTopToBaseline = baseline,
      )

    case DrawnOpaque(_, _) => BracketInfo.default

object PartiallyDrawn:
  final case class UndrawnBracket(justification: Option[Justification])               extends PartiallyDrawn
  final case class DrawnNotes(justification: Option[Justification], bb: BoundingBox)  extends PartiallyDrawn
  final case class DrawnText(justification: Option[Justification], bb: BoundingBox)   extends PartiallyDrawn
  final case class DrawnOpaque(justification: Option[Justification], bb: BoundingBox) extends PartiallyDrawn
