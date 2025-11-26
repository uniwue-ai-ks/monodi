package de.olyro.monodi
package data
package notes

sealed trait CommentTreeLeafContent derives io.circe.derivation.ConfiguredCodec
object CommentTreeLeafContent:
  import Comment.zeileContainerEncoder
  import Comment.zeileContainerDecoder

  final case class Text(content: String)          extends CommentTreeLeafContent
  final case class Bracket()                      extends CommentTreeLeafContent
  final case class Notes(content: ZeileContainer, context: Option[Boolean]) extends CommentTreeLeafContent
