package de.olyro.monodi.exprt.comment_tree

final case class BracketInfo(
    priority: Int,
    bracketHeight: Double,
    bracketInnerTopToBaseline: Double,
)

object BracketInfo:
  val bracketWidth                   = 10.0
  val bracketInnerDefaultHeight      = 20.0
  val bracketExtraHeightTopAndBottom = 5.0
  val bracketStrokeWidth             = 2.0
  val default                        = BracketInfo(priority = 0, bracketInnerDefaultHeight, bracketInnerDefaultHeight / 2.0)
