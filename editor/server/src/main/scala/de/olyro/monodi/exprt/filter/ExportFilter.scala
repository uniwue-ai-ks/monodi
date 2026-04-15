package de.olyro.monodi.exprt
package filter

trait ExportFilter:
  def sourceShouldBeExported(src: String): Boolean
  def documentShouldBeExported(doc: DocPublishInfo): Boolean
  def notesShouldBeExportedFor(doc: DocPublishInfo): Boolean
  def apparatusShouldBeExportedFor(doc: DocPublishInfo): Boolean

object ExportFilter:
  def ofStage(sources: List[SourcePublishInfo], config: ExportConfig.ExportSetConfig) =
    val sourcesToExport = sources
      .filter: s =>
        config.statusWhiteList.isEmpty || config.statusWhiteList.contains(s.publish.toLowerCase.trim)
      .map(_.id)

    new ExportFilter:
      override def sourceShouldBeExported(src: String): Boolean =
        sourcesToExport.contains(src)

      override def documentShouldBeExported(doc: DocPublishInfo): Boolean =
        sourcesToExport.contains(doc.quelle_id) && (config.statusWhiteList.isEmpty || config.statusWhiteList.contains(
          doc.publish.toLowerCase.trim
        ))

      override def notesShouldBeExportedFor(doc: DocPublishInfo): Boolean =
        documentShouldBeExported(
          doc
        ) && (config.documentNoteStatusWhiteList.isEmpty || config.documentNoteStatusWhiteList.contains(
          doc.publish.toLowerCase.trim
        ))

      override def apparatusShouldBeExportedFor(doc: DocPublishInfo): Boolean =
        notesShouldBeExportedFor(
          doc
        ) && (config.documentApparatusStatusWhiteList.isEmpty || config.documentApparatusStatusWhiteList.contains(
          doc.publish.toLowerCase.trim
        ))

  def global(sources: List[SourcePublishInfo], config: ExportConfig): ExportFilter =
    val makeAllDocs              = config.exportSets.exists(_.statusWhiteList.isEmpty)
    val makeAllNotes             = config.exportSets.exists(_.documentNoteStatusWhiteList.isEmpty)
    val makeAllApparati          = config.exportSets.exists(_.documentApparatusStatusWhiteList.isEmpty)
    val globalWhiteList          = config.exportSets.flatMap(_.statusWhiteList).distinct
    val globalNotesWhiteList     = config.exportSets.flatMap(_.documentNoteStatusWhiteList).distinct
    val globalApparatusWhiteList = config.exportSets.flatMap(_.documentApparatusStatusWhiteList).distinct

    val sourcesToExport = sources
      .filter: s =>
        makeAllDocs || globalWhiteList.contains(s.publish.toLowerCase.trim)
      .map(_.id)

    new ExportFilter:
      override def sourceShouldBeExported(src: String): Boolean =
        sourcesToExport.contains(src)

      override def documentShouldBeExported(doc: DocPublishInfo): Boolean =
        makeAllDocs || (sourceShouldBeExported(doc.quelle_id) && globalWhiteList.contains(
          doc.publish.toLowerCase.trim
        ))

      override def notesShouldBeExportedFor(doc: DocPublishInfo): Boolean =
        documentShouldBeExported(doc) && (makeAllNotes || globalNotesWhiteList.contains(
          doc.publish.toLowerCase.trim
        ))

      override def apparatusShouldBeExportedFor(doc: DocPublishInfo): Boolean =
        notesShouldBeExportedFor(doc) && (makeAllApparati || globalApparatusWhiteList.contains(
          doc.publish.toLowerCase.trim
        ))
