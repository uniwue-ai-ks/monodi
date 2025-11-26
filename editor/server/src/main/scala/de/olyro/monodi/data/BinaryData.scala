package de.olyro.monodi
package data

import java.io.*
import java.nio.charset.*
import java.util.*
import scala.util.*

final case class BinaryData(content: Array[Byte]):
  def toInputStream: InputStream = new ByteArrayInputStream(content)

object BinaryData:
  import io.circe.*

  def utf8BytesFrom(s: String) = BinaryData(s.getBytes(StandardCharsets.UTF_8))

  given enc: Encoder[BinaryData] = Encoder[String].contramap[BinaryData](
    b => Base64.getEncoder.encodeToString(b.content))

  given dec: Decoder[BinaryData] = Decoder[String].emapTry[BinaryData](
    s => Try { BinaryData(Base64.getDecoder.decode(s)) })
