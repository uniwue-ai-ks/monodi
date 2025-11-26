package de.olyro.monodi
package exprt
package print

import cover as C
import hierarchy as H
import H.TocEntityId

final case class PrintStage1(
    items: Vector[PrintStage1.Item],
    tocInfo: Map[TocEntityId, Int]
):
  def addPages(newPages: Seq[BoundingBox]): PrintStage1 =
    copy(items = items ++ newPages.map(PrintStage1.Item.Page.apply))

  def addPage(newPage: BoundingBox): PrintStage1 =
    addPages(Seq(newPage))

  def addCover(cover: C.Cover): PrintStage1 =
    copy(items = items :+ PrintStage1.Item.Cover(cover))

  def addToc(toc: List[H.TableOfContentsViewItem], numPages: Int): PrintStage1 =
    copy(items = items :+ PrintStage1.Item.TableOfContents(toc, numPages))

  def register(info: TocEntityId, pageNumber: Int): PrintStage1 =
    copy(tocInfo = tocInfo.updated(info, pageNumber))

  def registerDoc(docId: String, pageNumber: Int): PrintStage1               = register(TocEntityId.Document(docId), pageNumber)
  def registerSourceDesc(sourceId: String, pageNumber: Int): PrintStage1     =
    register(TocEntityId.SourceDescription(sourceId), pageNumber)
  def registerCriticalApparatus(docId: String, pageNumber: Int): PrintStage1 =
    register(TocEntityId.CriticalApparatus(docId), pageNumber)

  def pageCount: Int = items.map(_.pageCount).sum

object PrintStage1:
  enum Item derives CanEqual:
    case Page(box: BoundingBox)
    case Cover(cover: C.Cover)
    case TableOfContents(toc: List[H.TableOfContentsViewItem], pages: Int)

    def pageCount: Int = this match
      case Page(_)                   => 1
      case Cover(_)                  => 1
      case TableOfContents(_, pages) => pages

    def hasPageNumbers: Boolean = this match
      case Page(box)             => true
      case Cover(_)              => false
      case TableOfContents(_, _) => false

  val empty = PrintStage1(Vector.empty, Map.empty)
