package de.olyro

import io.circe.derivation.Configuration

package object monodi:
  given circeDerivationConfig: Configuration        = Configuration.default.withDiscriminator("kind")
  given CanEqual[org.http4s.Method, org.http4s.Method]     = CanEqual.derived
  given CanEqual[org.http4s.Uri.Path, org.http4s.Uri.Path] = CanEqual.derived
  given CanEqual[io.circe.Json, io.circe.Json]             = CanEqual.derived
  given CanEqual[org.xlsx4j.sml.STCellType, org.xlsx4j.sml.STCellType] = CanEqual.derived
  given CanEqual[cats.kernel.Comparison, cats.kernel.Comparison] = CanEqual.derived
