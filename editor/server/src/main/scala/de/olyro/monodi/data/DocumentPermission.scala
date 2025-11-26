package de.olyro.monodi
package data

final case class DocumentPermission(
  user: User,
  document: Int,
  canWrite: Boolean
)
