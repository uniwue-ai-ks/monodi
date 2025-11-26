package de.olyro.monodi.exprt.print.markdown

import de.olyro.monodi.exprt.print.Size
import de.olyro.monodi.exprt.Linebreak
import de.olyro.monodi.exprt.BoundingBox
import de.olyro.monodi.exprt.TextBox
import de.olyro.monodi.exprt.Svg
import de.olyro.monodi.exprt.Sub
import de.olyro.monodi.exprt.Point
import scalatags.Text.all.*
import scalatags.Text.{svgAttrs as SA, svgTags as ST}

enum Run:
  case Text(text: String, size: Size, style: RunStyle)
  case Image(src: String, width: Size)

  def inHeading(level: Int): Run = this match
    case Run.Text(text, size, style) =>
      val sizeFactor = 1.0 + (0.3 * (7 - level))
      Run.Text(text, Size.OfSyllableFontSize(sizeFactor), style)
    case img: Run.Image => img

  def inStrongEmphasis: Run = this match
    case Run.Text(text, size, _) => Run.Text(text, size, RunStyle.SmallCaps)
    case img: Run.Image          => img

  def inEmphasis: Run = this match
    case Run.Text(text, size, style) => Run.Text(text, size, RunStyle.Italic)
    case img: Run.Image              => img

object Run:
  given (svg: Svg) => Linebreak.Run[Run]:
    def baseline(bb: BoundingBox): Double =
      bb.resolve("baseline").head.y

    extension (run: Run)
      def render: BoundingBox = run match
        case run: Run.Text =>
          run.style match
            case RunStyle.Normal    =>
              TextBox.normal(
                run.text,
                svg.config.useSystemFontNames,
                setMetricMarkers = true,
                fontSize = Some(run.size.toDouble(svg))
              )
            case RunStyle.Italic    =>
              TextBox.italic(
                run.text,
                svg.config.useSystemFontNames,
                setMetricMarkers = true,
                fontSize = Some(run.size.toDouble(svg))
              )
            case RunStyle.SmallCaps =>
              TextBox.smallCaps(
                run.text,
                svg.config.useSystemFontNames,
                setMetricMarkers = true,
                fontSize = Some(run.size.toDouble(svg))
              )
        case run: Run.Image =>
          val baselineBB = BoundingBox(0, 0, Nil, Some("baseline"), full=false)
          BoundingBox(
              width = run.width.toDouble(svg),
              height = run.width.toDouble(svg),
              subs = List(
                Sub(
                  start = Point(0, 0),
                  sub = Right(
                    p =>
                      ST.image(
                        href := run.src,
                        SA.x := p.x.toString,
                        SA.y := p.y.toString,
                        SA.width := run.width.toDouble(svg).toString,
                        SA.height := run.width.toDouble(svg).toString
                      )
                  )
                )
              )
            ).addAt(baselineBB, Point(0, run.width.toDouble(svg) * 0.8))


      def split: Option[(Run, Run)] =
        run match
          case run: Run.Text =>
            val words = run.text.split("\\s+")
            if words.length <= 1 then None
            else
              val mid       = words.length / 2
              val firstText = words.take(mid).mkString(" ")
              val secondText = words.drop(mid).mkString(" ")
              Some(
                (
                  Run.Text(firstText, run.size, run.style),
                  Run.Text(secondText, run.size, run.style)
                )
              )
          case _: Run.Image => None
