package de.olyro.monodi
package exprt

import de.olyro.monodi.data.notes.ParatextComment
import de.olyro.monodi.data.notes.Comment
import de.olyro.monodi.data.notes.Container

final case class CommentData(
    comment: Either[ParatextComment, Comment],
    container: Container,
    signatures: List[String],
):
  def prependSignature(sig: String): CommentData = copy(signatures = sig :: signatures)

