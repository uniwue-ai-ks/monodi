package de.olyro.monodi
package data
package notes

sealed trait CommentTree derives io.circe.derivation.ConfiguredCodec:
  def id: String

object CommentTree:
  final case class CommentTreeUndecided(id: String) extends CommentTree

  final case class CommentTreeLeaf(
      id: String,
      content: CommentTreeLeafContent,
      justification: Option[Justification]
  ) extends CommentTree

  final case class CommentTreeGrid(
      id: String,
      items: List[List[CommentTree]],
      justification: Option[Justification]
  ) extends CommentTree
