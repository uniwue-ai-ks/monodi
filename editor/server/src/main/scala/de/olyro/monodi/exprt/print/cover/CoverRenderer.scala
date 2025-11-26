package de.olyro.monodi
package exprt
package print
package cover

import java.util.UUID

object CoverRenderer:
  def render(cover: Cover, svg: Svg): (BoundingBox, String) = cover match
    case Cover.TextCover(topItems, botItems) =>
      val (top, topCss) = renderTextCoverBlock(topItems, svg)
      val (bot, botCss) = renderTextCoverBlock(botItems, svg)
      val padding       = svg.config.height - top.height - bot.height
      (BoundingBox.concatY(List(top, bot), padding, None), topCss + "\n" + botCss)

  private def renderTextCoverBlock(items: List[LineItem], svg: Svg): (BoundingBox, String) =
    val prefix         = "text-class-" + UUID.randomUUID().toString
    val lines          = items.map(renderTextCoverItem(_, svg))
    val maxWidth       = lines.map(_.width).maxOption.getOrElse(0.0)
    val localCentered  = lines.map(_.centerX(maxWidth / 2).widenX(maxWidth))
    val globalCentered = localCentered.map(box => box.centerX(svg.config.width / 2).widenX(svg.config.width))
    val ided           = globalCentered.zipWithIndex.map((box, i) => box.copy(id = Some(s"$prefix-$i")))
    (BoundingBox.concatY(ided, 0, None), makeCssTextColors(prefix, items))

  private def renderTextCoverItem(item: LineItem, svg: Svg): BoundingBox =
    item match
      case LineItem.Text(text, size, _, _) =>
        TextBox.normal(text, true, None, false, Some(size.toDouble(svg)))
      case LineItem.VSkip(size)         =>
        BoundingBox.empty.widenY(size.toDouble(svg))

  private def makeCssTextColors(prefix: String, items: List[LineItem]): String =
    items.zipWithIndex
      .flatMap((item, i) =>
        item match
          case LineItem.Text(_, _, Some(color), _) => Some(s".$prefix-$i text { fill: $color; }")
          case LineItem.Text(_, _, None, _)        => None
          case LineItem.VSkip(_)                   => None
      )
      .mkString("\n")
