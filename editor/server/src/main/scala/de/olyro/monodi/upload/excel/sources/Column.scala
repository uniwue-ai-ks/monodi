package de.olyro.monodi
package upload
package excel
package source

sealed trait Column derives CanEqual
object Column:
  case object Quellensigle         extends Column
  case object Herkunftsregion      extends Column
  case object Herkunftsort         extends Column
  case object Herkunftsinstitution extends Column
  case object Ordenstradition      extends Column
  case object Quellentyp           extends Column
  case object Bibliotheksort       extends Column
  case object Bibliothek           extends Column
  case object Bibliothessignatur   extends Column
  case object Kommentar            extends Column
  case object Datierung            extends Column
  case object Jahrhundert          extends Column
  case object Status               extends Column
  case object Manifest             extends Column
  case object Foliooffset          extends Column
  case object Publish              extends Column
  case object Beschreibung         extends Column
  case object Cantus_Siglum        extends Column
  case object Cantus_Century       extends Column

  val allColumns: List[Column] = List(
    Quellensigle,
    Herkunftsregion,
    Herkunftsort,
    Herkunftsinstitution,
    Ordenstradition,
    Quellentyp,
    Bibliotheksort,
    Bibliothek,
    Bibliothessignatur,
    Datierung,
    Jahrhundert,
    Kommentar,
    Status,
    Manifest,
    Foliooffset,
    Publish,
    Beschreibung,
    Cantus_Siglum,
    Cantus_Century,
  )

  def parse(s: String): Option[Column] =
    allColumns.find(c => normalize(c.toString) == normalize(s))

  def normalize(s: String): String =
    s.split("\\(")(0).toLowerCase().replaceAll("(?U)[^\\p{IsAlphabetic}[0-9]]", "")
