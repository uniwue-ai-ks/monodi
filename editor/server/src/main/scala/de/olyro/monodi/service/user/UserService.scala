package de.olyro.monodi
package service
package user

import cats.implicits.*
import data.*
import db.*
import doobie.implicits.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import Result.{Ok as ROk, *}

object UserService:
  val list = AuthRoutes.ofRole(Role.Admin)({ case POST -> Root / "api" / "user" / "list" =>
    _ =>
      for
        infos  <- DBRunner.run(for
                    users     <- DBRunner.listUsers
                    withRoles <- users.traverse(u => DBRunner.listRoles(u).map(r => UserInfo(u, r)))
                  yield withRoles)
        result <- Ok(UserInfosRetrieved(infos))
      yield result
  })

  val remove = AuthRoutes.ofRole(Role.Admin)({ case req @ POST -> Root / "api" / "user" / "remove" =>
    self =>
      for
        input  <- req.as[String]
        result <- User.parse(input) match {
                    case None       => Ok(InvalidUsernameFormat)
                    case Some(user) =>
                      if user == self then Ok(TriedToRemoveSelf)
                      else DBRunner.run(DBRunner.removeUser(user)) *> Ok(ROk)
                  }
      yield result
  })

  val create = AuthRoutes.ofRole(Role.Admin)({ case req @ POST -> Root / "api" / "user" / "create" =>
    _ =>
      for
        creation <- req.as[UserCreation]
        result   <- User.parse(creation.user) match {
                      case None       => Ok(InvalidUsernameFormat)
                      case Some(user) =>
                        for
                          wasCreated <- DBRunner.run(DBRunner.createUserOrUpdatePassword(user, creation.password))
                          result     <- if wasCreated then Ok(ROk) else Ok(UserAlreadyExists)
                        yield result
                    }
      yield result
  })

