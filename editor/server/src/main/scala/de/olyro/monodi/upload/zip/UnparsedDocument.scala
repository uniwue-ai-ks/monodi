package de.olyro.monodi
package upload
package zip

import cats.*, cats.data.*, Validated.*, cats.implicits.*
import java.util.zip.*

final case class UnparsedDocument(
  id: String,
  meta: Option[ZipEntry],
  data: Option[ZipEntry],
  writers: Option[ZipEntry],
  )

object UnparsedDocument:
  type Err[A] = ValidatedNel[String, A]

  given ValSemiGroup: Semigroup[Err[UnparsedDocument]]:
    def combine(aa: Err[UnparsedDocument], bb: Err[UnparsedDocument]): Err[UnparsedDocument] =
      Util.flattenVNel((aa, bb).mapN( (a, b) => 
        if a.id != b.id then Validated.invalidNel(s"Id of ${a.id} and ${b.id} was not valid")
        else (
          a.id.pure[Err],
          getOne(s"There were two metas for document ${a.id}")(a.meta, b.meta),
          getOne(s"There were two data for document ${a.id}")(a.data, b.data),
          getOne(s"There were two writers for document ${a.id}")(a.writers, b.writers),
        ).mapN(UnparsedDocument.apply)
    ))

  def getOne[A](message: String)(a: Option[A], b: Option[A]): Err[Option[A]] = (a, b) match
    case (Some(_), Some(_)) => Validated.invalidNel(message)
    case (None   , b      ) => Valid(b)
    case (a      , _      ) => Valid(a)
