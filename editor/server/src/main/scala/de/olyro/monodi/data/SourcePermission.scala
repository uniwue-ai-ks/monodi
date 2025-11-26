package de.olyro.monodi
package data

final case class SourcePermission(
  user: User,
  source: Int,
  canWrite: Boolean
)
