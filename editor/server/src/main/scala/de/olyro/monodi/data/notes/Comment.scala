package de.olyro.monodi
package data
package notes

import io.circe.*
import io.circe.generic.semiauto.*

final case class Comment(
    startUUID: String,
    endUUID: String,
    text: String,
    emendation: Option[Boolean],
    line: Option[ZeileContainer],
    tree: Option[CommentTree],
):
  def concatenatedIds: String = s"$startUUID-$endUUID"

object Comment:
  given zeileContainerEncoder: Encoder[ZeileContainer] =
    Encoder[Container].contramap[ZeileContainer](zc => zc)

  given zeileContainerDecoder: Decoder[ZeileContainer] =
    Decoder[Container].emap({
      case zc: ZeileContainer => Right(zc)
      case c                  => Left(s"wrong container: ${c.getClass.toString}")
    })

  given encoder: Encoder[Comment] = deriveEncoder[Comment]
  given decoder: Decoder[Comment] = deriveDecoder[Comment]

