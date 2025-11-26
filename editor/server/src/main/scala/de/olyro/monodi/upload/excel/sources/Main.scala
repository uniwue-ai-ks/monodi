package de.olyro.monodi
package upload
package excel
package source

import java.nio.file.*

object Main:
  def main(args: Array[String]): Unit =
    val bytes = Files.readAllBytes(Paths.get("/tmp/table.xlsx"))
    println(ExcelParser.parseTable(bytes))
