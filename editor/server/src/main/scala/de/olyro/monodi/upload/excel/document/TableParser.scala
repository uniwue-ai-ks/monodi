package de.olyro.monodi
package upload
package excel
package document

import cats.implicits.*, cats.data.*
import data.{Document, User}
import Column.*

object TableParser:
  def parse(table: Vector[Vector[String]]): Either[String, (List[RowError], List[ParsedSource])] =
    for
      (firstRow, rest) <- splitHead(table)
      _                 = println(firstRow)
      header           <- findColumns(firstRow)
      (errors, docs)    = rest.zipWithIndex.foldMap({ case (row, index) => parseRow(index + 1, project(header, row)) })
      _                <- assertNoDoubleIds(docs.map(_._1))
    yield (errors, collect(docs))

  def collect(l: List[(Document, List[User])]): List[ParsedSource] =
    l.foldLeft(Map.empty[String, ParsedSource])(addDocument).values.toList

  def addDocument(accu: Map[String, ParsedSource], d: (Document, List[User])): Map[String, ParsedSource] =
    val pd = ParsedDocument(d._1.id, Some(d._1), None, Some(d._2))
    accu.get(d._1.quelle_id) match
      case None     => accu + (d._1.quelle_id -> (ParsedSource(d._1.quelle_id, None, List(pd))))
      case Some(ps) => accu + (d._1.quelle_id -> (ParsedSource(d._1.quelle_id, None, pd :: ps.documents)))

  def assertNoDoubleIds(l: List[Document]): Either[String, Unit] =
    getIdCount(l).find(_._2 > 1) match
      case None          => Right(())
      case Some((id, _)) => Left(s"the documentID $id has been used multiple times")

  def getIdCount(l: List[Document]): Map[String, Int] =
    l.map(d => Map(d.id -> 1)).combineAll

  def parseRow(rowName: Int, projection: Map[Column, String]): (List[RowError], List[(Document, List[User])]) =
    def getAdditional: Map[String, String] =
      List(
        Überlieferungszustand,
        Zusatz_zu_Textinitium,
        Bezugsgesang,
        Melodiennummer_Katalog,
        Melodie_Standard,
        Melodie_Quelle,
        Startposition,
        Endseite,
        Endzeile,
        Nachtragsschicht,
        Referenz_auf_Spiel,
        SpielName,
        Editor,
        IIIFs,
        FestUndGattung
      ).map(column => fixKeyName(column.toString) -> projection.get(column).getOrElse("")).toMap

    def fixKeyName(key: String): String =
      if key == IIIFs.toString then "iiifs"
      else key

    // format: off
    if projection.values.forall(_.isEmpty) then (Nil, Nil)
    else (projection.get(Doc_Id), projection.get(Quellen_ID), projection.get(Editor).map(User.parse), projection.get(Schreibzugriff).map(_.split("\\s*;\\s*").toList.traverse(User.parse))) match
      case (None     , _          , _         , _         ) => (Nil                                                                                        , Nil)
      case (_        , None       , _         , _         ) => (List(RowError(rowName, "Sowohl die Doc_Id als auch Quellen_ID Spalten müssen Werte haben")), Nil)
      case (_        , _          , Some(None), _         ) => (List(RowError(rowName, "Der Editor konnte nicht geparst werden. Vielleicht ist es keine valide Mail Adresse")), Nil)
      case (_        , _          , _         , Some(None)) => (List(RowError(rowName, "Die Schreibzugriffe konnten nicht geparst werden. Vielleicht ist einer von Ihnen keine Mail Adresse?")), Nil)
      case (Some(did),  Some(qid) , editor    , writers   ) => {
        (
          Nil,
          List(
            (
              Document(
                did,
                qid,
                projection.get(Dokumenten_ID).getOrElse(""),
                projection.get(Gattung1).getOrElse(""),
                projection.get(Gattung2).getOrElse(""),
                projection.get(Fest).getOrElse(""),
                projection.get(Feier).getOrElse(""),
                projection.get(Textinitium_Editionseinheit).getOrElse(""),
                projection.get(Nachweis_Editionseinheit).getOrElse(""),
                projection.get(Druckausgabe).getOrElse(""),
                projection.get(Startzeile).getOrElse(""),
                projection.get(Startseite).getOrElse(""),
                projection.get(Kommentar).getOrElse(""),
                projection.get(Editionsstatus).getOrElse(""),
                getAdditional,
                projection.get(Publish).getOrElse("none"),
              ),
            (editor.flatten.fold(List.empty[User])(List(_)) ++ writers.flatten.getOrElse(List.empty[User])).distinct
            )
          )
        )
      }
   // format: on

  def project(header: Map[Column, Int], row: Vector[String]): Map[Column, String] =
    header.toList
      .flatMap({ case (k, v) =>
        row.get(v.toLong) match {
          case None    => Nil
          case Some(s) => {
            val ss = s.replace("\u00A0", " ").trim
            if ss.isEmpty || ss == "--" then Nil else List(k -> ss)
          }
        }
      })
      .toMap

  def splitHead(table: Vector[Vector[String]]): Either[String, (Vector[String], Vector[Vector[String]])] =
    table match
      case head +: rest => Right((head, rest))
      case _            => Left("Jede Excel-Tabelle muss mindestens zwei Zeilen lang sein.")

  def findColumns(firstRow: Vector[String]): Either[String, Map[Column, Int]] =
    def findColumn(c: Column): ValidatedNel[Column, Int] =
      firstRow.zipWithIndex.find({ case (s, _) => Column.parse(s) == Some(c) }).map(_._2) match
        case None    => Validated.invalidNel(c)
        case Some(i) => Validated.valid(i)

    Column.allColumns
      .traverse(c => findColumn(c).map(c -> _))
      .map(_.toMap)
      .toEither
      .leftMap(_.map(_.toString).toList.mkString(", "))
      .leftMap("Es fehlen folgende Spalten, um die Tabelle parsen zu können: " + _)
