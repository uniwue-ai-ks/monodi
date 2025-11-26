package de.olyro.monodi
package upload
package excel
package document

sealed trait Column derives CanEqual
object Column:
  case object Dokumenten_ID               extends Column
  case object Gattung1                    extends Column
  case object Gattung2                    extends Column
  case object Fest                        extends Column
  case object Feier                       extends Column
  case object Textinitium_Editionseinheit extends Column
  case object Überlieferungszustand       extends Column
  case object Zusatz_zu_Textinitium       extends Column
  case object Bezugsgesang                extends Column
  case object Nachweis_Editionseinheit    extends Column
  case object Melodiennummer_Katalog      extends Column
  case object Melodie_Standard            extends Column
  case object Melodie_Quelle              extends Column
  case object Startseite                  extends Column
  case object Startzeile                  extends Column
  case object Startposition               extends Column
  case object Endseite                    extends Column
  case object Endzeile                    extends Column
  case object Nachtragsschicht            extends Column
  case object Quellensigle                extends Column
  case object Druckausgabe                extends Column
  case object Referenz_auf_Spiel          extends Column
  case object Editionsstatus              extends Column
  case object Editor                      extends Column
  case object Schreibzugriff              extends Column
  case object Kommentar                   extends Column
  case object Doc_Id                      extends Column
  case object Quellen_ID                  extends Column
  case object Publish                     extends Column
  case object IIIFs                       extends Column

  val allColumns: List[Column] = List(
    Dokumenten_ID,
    Gattung1,
    Gattung2,
    Fest,
    Feier,
    Textinitium_Editionseinheit,
    Überlieferungszustand,
    Zusatz_zu_Textinitium,
    Bezugsgesang,
    Nachweis_Editionseinheit,
    Melodiennummer_Katalog,
    Melodie_Standard,
    Melodie_Quelle,
    Startseite,
    Startzeile,
    Startposition,
    Endseite,
    Endzeile,
    Nachtragsschicht,
    Quellensigle,
    Druckausgabe,
    Referenz_auf_Spiel,
    Editionsstatus,
    Editor,
    Schreibzugriff,
    Kommentar,
    Doc_Id,
    Quellen_ID,
    Publish,
    IIIFs
  )

  def parse(s: String): Option[Column] =
    allColumns.find(c => normalize(c.toString) == normalize(s))

  def normalize(s: String): String =
    s.split("\\(")(0).toLowerCase().replaceAll("(?U)[^\\p{IsAlphabetic}[0-9]]", "")
