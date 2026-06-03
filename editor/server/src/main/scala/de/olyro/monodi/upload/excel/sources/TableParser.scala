package de.olyro.monodi
package upload
package excel
package source

import cats.implicits.*, cats.data.*
import data.*

object TableParser:
  def parse(table: Vector[Vector[String]]): Either[String, (List[RowError], List[ParsedSource])]    =
    for
      (firstRow, rest) <- splitHead(table)
      _                 = println(firstRow)
      header           <- findColumns(firstRow)
    yield rest.zipWithIndex.foldMap({ case (row, index) => parseRow(index + 1, project(header, row)) })

  def parseRow(rowName: Int, projection: Map[Column, String]): (List[RowError], List[ParsedSource]) =
    if projection.values.forall(_.isEmpty) then (Nil, Nil)
    else
      projection.get(Column.Quellensigle) match
        case None    => (List(RowError(rowName, "Jede Zeile muss ein Quellensigle haben")), Nil)
        case Some(s) =>
          if s.trim.isEmpty then (List(RowError(rowName, "Jede Zeile muss ein Quellensigle haben")), Nil)
          else
            (
              Nil,
              List(
                ParsedSource(
                  s,
                  Some(
                    Source(
                      s,
                      s,
                      projection.getOrElse(Column.Herkunftsregion, ""),
                      projection.getOrElse(Column.Herkunftsort, ""),
                      projection.getOrElse(Column.Herkunftsinstitution, ""),
                      projection.getOrElse(Column.Ordenstradition, ""),
                      projection.getOrElse(Column.Quellentyp, ""),
                      projection.getOrElse(Column.Bibliotheksort, ""),
                      projection.getOrElse(Column.Bibliothek, ""),
                      projection.getOrElse(Column.Bibliothessignatur, ""),
                      projection.getOrElse(Column.Kommentar, ""),
                      projection.getOrElse(Column.Datierung, ""),
                      projection.getOrElse(Column.Status, ""),
                      projection.getOrElse(Column.Jahrhundert, ""),
                      projection.getOrElse(Column.Manifest, ""),
                      projection.getOrElse(Column.Foliooffset, ""),
                      projection.getOrElse(Column.Publish, "none"),
                      projection.getOrElse(Column.Beschreibung, ""),
                      projection.getOrElse(Column.Cantus_Siglum, ""),
                      projection.getOrElse(Column.Cantus_Century, ""),
                    ),
                  ),
                  Nil,
                ),
              ),
            )

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
    def findColumn(c: Column): ValidatedNel[Column, List[Int]] =
      (c, firstRow.zipWithIndex.find({ case (s, _) => Column.parse(s) == Some(c) }).map(_._2)) match
        case (_, Some(i))                  => Validated.valid(List(i))
        case (Column.Jahrhundert, None)    => Validated.valid(Nil)
        case (Column.Cantus_Siglum, None)  => Validated.valid(Nil)
        case (Column.Cantus_Century, None) => Validated.valid(Nil)
        case (_, None)                     => Validated.invalidNel(c)

    Column.allColumns
      .flatTraverse(c => findColumn(c).map(_.map(i => c -> i)))
      .map(_.toMap)
      .toEither
      .leftMap(_.map(_.toString).toList.mkString(", "))
      .leftMap("Es fehlen folgende Spalten, um die Tabelle parsen zu können: " + _)
