package de.olyro.monodi.exprt.print.bookbinding

import de.olyro.monodi.data.Document
import de.olyro.monodi.data.notes.RootContainer
import de.olyro.monodi.data.Source

final case class DocWithSource(
    doc: Document,
    notes: RootContainer,
    source: Source
)
