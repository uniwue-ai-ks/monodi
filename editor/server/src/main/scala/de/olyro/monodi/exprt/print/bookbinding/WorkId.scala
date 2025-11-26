package de.olyro.monodi.exprt.print.bookbinding

import java.util.UUID
import io.circe.*

opaque type WorkId = UUID

object WorkId:
  def random(): WorkId = UUID.randomUUID()

  given CanEqual[WorkId, WorkId] = CanEqual.derived
  given Encoder[WorkId] = Encoder.encodeUUID
  given Decoder[WorkId] = Decoder.decodeUUID
