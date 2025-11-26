package de.olyro.monodi.exprt.print.hierarchy

final case class TableOfContents(
    items: List[TableOfContentsItem]
):
  def toView: TableOfContentsView =
    def go(indexPathToCollection: List[Int], items: List[TableOfContentsItem]): List[TableOfContentsViewItem] =
      items.zipWithIndex.flatMap: (item, index) =>
        val pathToCurrent = indexPathToCollection :+ index
        val depth         = pathToCurrent.length - 1
        TableOfContentsViewItem(
          depth = depth,
          text = item.text,
          entityId = item.entityId
        ) :: go(pathToCurrent, item.children)

    TableOfContentsView(items = go(Nil, items))
