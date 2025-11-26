package de.olyro.monodi
package db

import cats.implicits.*
import doobie.*
import io.circe.*, io.circe.parser.*, io.circe.syntax.*
import org.postgresql.util.PGobject

object MetaInstances:
  given JsonMeta: Meta[Json] =
    Meta.Advanced
      .other[PGobject]("json")
      .timap[Json](a => parse(a.getValue).leftMap[Json](e => throw e).merge)(a => {
        val o = new PGobject
        o.setType("json")
        o.setValue(a.noSpaces)
        o
      })

  given mapssMeta: Meta[Map[String, String]] = JsonMeta.timap(json =>
    Decoder[Map[String, String]].apply(json.hcursor) match {
      case Left(t)  => throw t
      case Right(m) => m
    },
  )(_.asJson)
