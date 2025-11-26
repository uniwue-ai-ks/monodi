package de.olyro.monodi.exprt.print

import de.olyro.monodi.data.notes.RootContainer
import cover as C
import hierarchy as H
import de.olyro.monodi.data.notes.Justification
import de.olyro.monodi.data.Source

enum BookEntry derives CanEqual:
  case MetaData(lines: List[LineItem])
  case Document(id: String, content: RootContainer)
  case Cover(cover: C.Cover)
  case TableOfContents(toc: List[H.TableOfContentsViewItem])
  case SourceDescription(source: Source)
  case CriticalApparatus(documentId: String, content: RootContainer)

object BookEntry:
  def ofMetaStrings(strings: List[String]): BookEntry =
    MetaData(strings.map(LineItem.Text(_, Size.sameAsSyllable, None, Some(Justification.Left))))
