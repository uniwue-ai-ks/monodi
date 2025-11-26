package de.olyro.monodi
package exprt
package print
package bookbinding

final case class DefaultDocData(
    genre: String,
    libLocation: String,
    libName: String,
    libSig: String,
    editorName: String,
    editorCode: String,
    century: String
)

object DefaultDocData:
  def gather(
      docs: Seq[DocWithSource],
      editorCodes: Map[String, String],
      editorNames: Map[String, String]
  ): Option[DefaultDocData] =
    val docSet = docs.toSet
    for
      genre       <- getSingle(docSet, d => genrePlural.getOrElse(d.doc.gattung1, d.doc.gattung1))
      libLocation <- getSingle(docSet, _.source.bibliotheksort)
      libName     <- getSingle(docSet, _.source.bibliothek)
      libSig      <- getSingle(docSet, _.source.bibliothekssignatur)
      editor      <- getSingle(docSet, _.doc.additionalData.getOrElse("Editor", ""))
      editorName   = editorNames.getOrElse(editor, "")
      editorCode   = editorCodes.getOrElse(editor, "")
      century     <- getSingle(docSet, _.source.datierung)
    yield DefaultDocData(genre, libLocation, libName, libSig, editorName, editorCode, century)

  private def getSingle(docs: Set[DocWithSource], getter: DocWithSource => String): Option[String] =
    docs.map(getter).toList match
      case Nil       => None
      case "" :: Nil => None
      case x :: Nil  => Some(x)
      case _         => None

  private val genrePlural: Map[String, String] = Map(
    "Lied"          -> "Lieder",
    "Sequenz"       -> "Sequenzen",
    "Tropus"        -> "Tropen",
    "Antiphon"      -> "Antiphonen",
    "Agnus"         -> "Ordinariumsgesänge",
    "Benedicamus"   -> "Ordinariumsgesänge",
    "Credo"         -> "Ordinariumsgesänge",
    "Gloria"        -> "Ordinariumsgesänge",
    "Ite missa est" -> "Ordinariumsgesänge",
    "Kyrie"         -> "Ordinariumsgesänge",
    "Requiescant"   -> "Ordinariumsgesänge",
    "Sanctus"       -> "Ordinariumsgesänge",
    "Humiliate vos" -> "Ordinariumsgesänge",
    "Spiele-Passus" -> "Liturgisches Spiel"
  )
