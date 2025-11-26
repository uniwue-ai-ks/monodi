package de.olyro.monodi
package upload
package excel

import cats.*, cats.implicits.*
import java.io.{InputStream, ByteArrayInputStream}
import org.docx4j.openpackaging.packages.{OpcPackage, SpreadsheetMLPackage}
import org.xlsx4j.sml.{Cell as DCell, STCellType, CTRst}
import scala.jdk.CollectionConverters.*

object ExcelParser:
  def parseTable(bytes: Array[Byte]): Either[String, Vector[Vector[String]]] = 
    parseTable(new ByteArrayInputStream(bytes))

  def parseTable(in: InputStream): Either[String, Vector[Vector[String]]] = try
    val workbook      = log("getting workbook", OpcPackage.load(in).asInstanceOf[SpreadsheetMLPackage].getWorkbookPart)
    val sharedStrings = log("getting shared strings", Option(workbook.getSharedStrings).map(_.getContents.getSi.asScala.toList).getOrElse(List()))
    val firstSheet    = log("getting first sheet", workbook.getWorksheet(0).getContents)
    val rows          = firstSheet.getSheetData.getRow.asScala.toVector.map(
      row => log(s"getting row ${row.getR.toString}", {
        val cells = row.getC.asScala.toVector
        val allCells = cells.map(ColRef.fromCell).maximumOption(using ColRef.colRefOder) match {
          case None      => Vector.empty
          case Some(max) => {
            val allCellRefs = ColRef.getPredecessors(max.inc)
            allCellRefs.flatMap(ref => log(s"getting cell ${ref.toString}", cells.find(c => ColRef.fromCell(c) == ref) match {
              case None    => Vector("")
              case Some(c) => Vector(getCellValue(c, sharedStrings))
            }))
          }
        }
        allCells
      })
    )
    Right(rows.reverse.dropWhile(_.forall(_.trim.isEmpty)).reverse)
  catch
    case e: Exception => Left(Util.stringify(e))


  def getCellValue(c: DCell, sharedStrings: List[CTRst]): String =
    (c.getV, c.getT) match
      case (null, _                 ) => ""
      case (v, STCellType.N         ) => v
      case (_, STCellType.INLINE_STR) => c.getIs.getT.getValue
      case (v, STCellType.S         ) => resolveSharedString(v.toInt, sharedStrings)
      case (v, STCellType.STR       ) => v
      case (_, cellType             ) => throw new RuntimeException("unkown cell " + c.getR + " with " + cellType.toString())

  def resolveSharedString(id: Int, sharedStrings: List[CTRst]): String =
    val sharedString = sharedStrings(id)
    if sharedString.getT != null then
      sharedString.getT.getValue
    else
      sharedString.getR.asScala.map(_.getT.getValue).mkString("")

  final case class ColRef(ref: String) derives CanEqual:
    def inc: ColRef =
      val lastChar = ref.charAt(ref.length - 1);
      val init = ref.substring(0, ref.length - 1)
      if lastChar == 'Z' && init.isEmpty then ColRef("AA")
      else if lastChar == 'Z' then ColRef(ColRef(init).inc.ref + "A")
      else ColRef(init + (Character.toString((lastChar + 1).asInstanceOf[Char])).toString)

  object ColRef:
    given colRefOder: Order[ColRef] = new Order[ColRef]:
      def compare(c1: ColRef, c2: ColRef): Int =
        if c1.ref.length > c2.ref.length then 1
        else if c1.ref.length < c2.ref.length then -1
        else if c1.ref.length == 0 then 0
        else if c1.ref.charAt(0) > c2.ref.charAt(0) then 1
        else if c1.ref.charAt(0) < c2.ref.charAt(0) then -1
        else compare(ColRef(c1.ref.substring(1)), ColRef(c2.ref.substring(1)))

    def getPredecessors(c: ColRef): Vector[ColRef] =
      LazyList.iterate(ColRef("A"))(_.inc).takeWhile(_ != c).toVector

    def fromCell(c: DCell): ColRef = ColRef(c.getR.replaceAll("[0-9]", ""))

  def log[A](part: String, a: => A): A =
    try
      a
    catch
      case e: Exception => throw new RuntimeException(s"in $part: " + e.getMessage(), e)

