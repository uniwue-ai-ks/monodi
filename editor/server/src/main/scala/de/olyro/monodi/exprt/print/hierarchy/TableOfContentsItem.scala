package de.olyro.monodi.exprt.print.hierarchy

final case class TableOfContentsItem(
    text: String,
    entityId: Option[TocEntityId],
    children: List[TableOfContentsItem]
)
