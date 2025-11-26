package de.olyro.monodi
package db

import cats.implicits.*
import data.*
import doobie.*, doobie.implicits.*

object DbPermissions:
  def mayWriteDocument(user: User, document: String): ConnectionIO[Boolean] =
    (
      isAdmin(user),
      getDocumentPermission(user, document)
    ).mapN(_ || _)

  def isAdmin(user: User): ConnectionIO[Boolean] =
    listRoles(user).map(_.contains(Role.Admin))

  def getDocumentPermission(user: User, document: String): ConnectionIO[Boolean] =
    sql"""SELECT COUNT(*) FROM dokument_editor WHERE account = ${user.mail} AND dokument = $document""".query[Int].unique.map(_ > 0)

  def clearDocumentPermissions(document: String): ConnectionIO[Unit] =
    sql"""DELETE FROM dokument_editor WHERE dokument = $document""".update.run.void

  def listRoles(u: User): ConnectionIO[List[Role]] =
    sql"""SELECT role FROM role WHERE role.account_email = $u""".query[Role].to[List]

  def giveWriteAccess(document: String)(u: User): ConnectionIO[Unit] =
    sql"""INSERT INTO dokument_editor VALUES ($u, $document)""".update.run.void
