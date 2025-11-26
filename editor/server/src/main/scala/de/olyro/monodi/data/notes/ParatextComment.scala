package de.olyro.monodi.data.notes

final case class ParatextComment(emendation: Boolean, comment: String, alternativeText: String, tree: Option[CommentTree]) derives io.circe.Codec.AsObject
