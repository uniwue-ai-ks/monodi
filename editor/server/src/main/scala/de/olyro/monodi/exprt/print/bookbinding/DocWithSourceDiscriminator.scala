package de.olyro.monodi
package exprt
package print
package bookbinding

import hierarchy as H

final case class DocWithSourceDiscriminator(
    valueGetter: DocWithSource => String,
    numbering: Option[H.Numbering],
    vskipAfter: Boolean
)

object DocWithSourceDiscriminator:
  val gattung1     = DocWithSourceDiscriminator(_.doc.gattung1, Some(H.Numbering.Numbers), false)
  val herkunftsort = DocWithSourceDiscriminator(_.source.herkunftsort, Some(H.Numbering.Letters), false)
  val bibAndSource = DocWithSourceDiscriminator(makeBibAndSource, None, false)
  val datierung    = DocWithSourceDiscriminator(_.source.datierung, None, true)
  val dokumenteId  = DocWithSourceDiscriminator(_.doc.dokumenten_id, None, false)
  val tocText  = DocWithSourceDiscriminator(Bookbinder.tocEntryText, None, false)

  private def makeBibAndSource(dws: DocWithSource): String =
    s"${dws.source.bibliotheksort}, ${dws.source.bibliothek}, ${dws.source.bibliothekssignatur} | ${dws.source.id}"
