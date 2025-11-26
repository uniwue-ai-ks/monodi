package de.olyro.monodi
package service

import io.circe.*

final case class OkResponse[A](data: A)

object OkResponse: 
  given [A: Encoder] => Encoder[OkResponse[A]] = Encoder.instance((ok: OkResponse[A]) =>
    Json.obj(("kind", Json.fromString("Ok")), ("data", Encoder[A].apply(ok.data)))
  )
