package de.olyro.monodi
package data

case class Bibliotheksort(name: String, bibliotheken: List[Bibliothek])
case class Bibliothek(name: String, signaturen: List[Signatur])
case class Signatur(name: String, quellensigle: String)
case class Herkunftsregion(name: String, orte: List[Herkunftsort])
case class Herkunftsort(name: String,  institute: List[String])
case class Gattung(gattung1: String, kürzel: String, untergattungen: List[Untergattung])
case class Untergattung(gattung2: String, kürzel: String)
case class Fest(name: String, datum: String)
