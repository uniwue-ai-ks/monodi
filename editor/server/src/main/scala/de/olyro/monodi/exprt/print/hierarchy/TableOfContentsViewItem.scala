package de.olyro.monodi.exprt.print.hierarchy

final case class TableOfContentsViewItem(
    depth: Int,
    text: String,
    entityId: Option[TocEntityId]
)
