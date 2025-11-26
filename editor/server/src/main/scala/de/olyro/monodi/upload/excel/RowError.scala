package de.olyro.monodi
package upload
package excel

import cats.*
import cats.data.*

final case class RowError(row: Int, errors: NonEmptyList[String])

object RowError:
  def apply(row: Int, first: String, rest: String*): RowError = RowError(row, NonEmptyList(first, rest.toList))

  given Show[RowError]:
    def show(t: RowError): String = s"Row ${t.row.toString}: ${t.errors.toList.mkString(", ")}";
