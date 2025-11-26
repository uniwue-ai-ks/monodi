package de.olyro.monodi
package upload
package zip

import alleycats.std.map.*
import java.util.zip.*
import cats.*, cats.data.*, Validated.*, cats.implicits.*

final case class UnparsedSource(
  id: String,
  meta: Option[ZipEntry],
  documents: Map[String, UnparsedDocument]
)

object UnparsedSource:
  type Err[A] = ValidatedNel[String, A]
  given ValSemiGroup: Semigroup[Err[UnparsedSource]]:
    def combine(aa: Err[UnparsedSource], bb: Err[UnparsedSource]): Err[UnparsedSource] =
      Util.flattenVNel((aa, bb).mapN( (a, b) => 
        if a.id != b.id then Validated.invalidNel(s"Id of source ${a.id} and ${b.id} was not valid")
        else (
          a.id.pure[Err],
          UnparsedDocument.getOne(s"There were two metas for source ${a.id}")(a.meta, b.meta),
          (a.documents.view.mapValues(toValid).toMap |+| b.documents.view.mapValues(toValid).toMap).sequence,
        ).mapN(UnparsedSource.apply)
    ))

  def toValid[A](a: A): Err[A] = Valid(a)
