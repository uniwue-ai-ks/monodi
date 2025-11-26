package de.olyro.monodi
package exprt
package print

import scala.compiletime.*
import io.circe.*

enum Size:
  case OfPageWidth(fraction: Double)
  case OfPageHeight(fraction: Double)
  case OfSyllableFontSize(fraction: Double)

  def value = this match
    case OfPageWidth(fraction)        => fraction
    case OfPageHeight(fraction)       => fraction
    case OfSyllableFontSize(fraction) => fraction

  def toDouble(svg: Svg): Double = this match
    case OfPageWidth(fraction)        => fraction * svg.config.width
    case OfPageHeight(fraction)       => fraction * svg.config.height
    case OfSyllableFontSize(fraction) => fraction * svg.config.syllableFontSize

  def *(d: Double): Size = this match
    case OfPageWidth(fraction)        => OfPageWidth(fraction * d)
    case OfPageHeight(fraction)       => OfPageHeight(fraction * d)
    case OfSyllableFontSize(fraction) => OfSyllableFontSize(fraction * d)

object Size:
  extension (d: Double)
    inline def ofPW: Size.OfPageWidth =
      inline if d <= 0 then error("Size of PW must be greater than 0")
      else if d >= 1 then error("Size must be less than 1")
      else Size.OfPageWidth(d)

    inline def ofPH: Size.OfPageHeight =
      inline if d <= 0 then error("Size of PW must be greater than 0")
      else if d >= 1 then error("Size must be less than 1")
      else Size.OfPageHeight(d)

    inline def ofSFS: Size.OfSyllableFontSize =
      inline if d <= 0 then error("Size of SFS must be greater than 0")
      else Size.OfSyllableFontSize(d)

    inline def percentPW: Size.OfPageWidth         = Size.ofPW(d / 100.0)
    inline def percentPH: Size.OfPageHeight        = Size.ofPH(d / 100.0)
    inline def percentSFS: Size.OfSyllableFontSize = Size.ofSFS(d / 100.0)

  val sameAsSyllable = Size.OfSyllableFontSize(1.0)

  given Encoder[Size] = Encoder[(Double, String)].contramap[Size](size =>
    val unit = size match
      case Size.OfPageWidth(_)        => "pw"
      case Size.OfPageHeight(_)       => "ph"
      case Size.OfSyllableFontSize(_) => "sfs"

    (size.value, unit)
  )

  given Decoder[Size] = Decoder[(Double, String)].flatMap((size, unit) =>
    if size < 0 then Decoder.failedWithMessage("Size must be greater than 0")
    else
      unit match
        case "pw" =>
          if size >= 1 then Decoder.failedWithMessage("Size must be less than 1")
          else Decoder.const(Size.OfPageWidth(size))

        case "ph" =>
          if size >= 1 then Decoder.failedWithMessage("Size must be less than 1")
          else Decoder.const(Size.OfPageHeight(size))

        case "sfs" => Decoder.const(Size.OfSyllableFontSize(size))

        case _ => Decoder.failedWithMessage(s"Unknown unit $unit for size")
  )
