package com.vyulabs.update.distribution.graphql.utils

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.Materializer
import com.mongodb.client.model.{Filters, Updates}
import com.vyulabs.update.common.common.Common.UserName
import com.vyulabs.update.common.info.{UserInfo, UserRole}
import com.vyulabs.update.common.info.UserRole.UserRole
import com.vyulabs.update.distribution.mongo.{DatabaseCollections}
import com.vyulabs.update.distribution.users.{PasswordHash, ServerUserInfo, UserCredentials}
import org.bson.BsonDocument
import org.slf4j.LoggerFactory

import java.io.IOException
import scala.collection.JavaConverters.asJavaIterableConverter
import scala.concurrent.{ExecutionContext, Future}

trait UsersUtils extends SprayJsonSupport {
  private implicit val log = LoggerFactory.getLogger(this.getClass)

  protected implicit val system: ActorSystem
  protected implicit val materializer: Materializer
  protected implicit val executionContext: ExecutionContext

  protected val collections: DatabaseCollections

  def addUser(userName: UserName, role: UserRole, password: String): Future[Boolean] = {
    log.info(s"Add user ${userName} with role ${role}")
    for {
      result <- {
        val document = ServerUserInfo(userName, role.toString, PasswordHash(password))
        collections.Users_Info.insert(document).map(_ => true)
      }
    } yield result
  }

  def removeUser(userName: UserName): Future[Boolean] = {
    log.info(s"Remove user ${userName}")
    val filters = Filters.eq("userName", userName)
    for {
      profile <- {
        collections.Users_Info.delete(filters).map(_ > 0)
      }
    } yield profile
  }

  def changeUserPassword(userName: UserName, oldPassword: String, password: String): Future[Boolean] = {
    for {
      credentials <- getUserCredentials(userName)
      result <- credentials match {
        case Some(credentials) =>
          if (credentials.passwordHash.hash == PasswordHash.generatePasswordHash(oldPassword, credentials.passwordHash.salt)) {
            changeUserPassword(userName, password)
          } else {
            Future.failed(new IOException(s"Password verification error"))
          }
        case None =>
          Future.failed(new IOException(s"Password verification error"))
      }
    } yield result
  }

  def changeUserPassword(userName: UserName, password: String): Future[Boolean] = {
    log.info(s"Change user ${userName} password")
    val filters = Filters.eq("userName", userName)
    val hash = PasswordHash(password)
    val updates = Updates.set("content.passwordHash", hash)
    for {
      profile <- {
        collections.Users_Info.update(filters, r => ServerUserInfo(r.userName, r.role, hash)).map(_ > 0)
      }
    } yield profile
  }

  def getUserCredentials(userName: UserName): Future[Option[UserCredentials]] = {
    val filters = Filters.eq("content.userName", userName)
    for {
      info <- collections.Users_Info.find(filters).map(_.map(info => UserCredentials(
        UserRole.withName(info.role), info.passwordHash)).headOption)
    } yield info
  }

  def getUsersInfo(userName: Option[UserName] = None): Future[Seq[UserInfo]] = {
    val clientArg = userName.map(Filters.eq("content.userName", _))
    val args = clientArg.toSeq
    val filters = if (!args.isEmpty) Filters.and(args.asJava) else new BsonDocument()
    for {
      info <- collections.Users_Info.find(filters).map(_.map(info => UserInfo(info.userName, UserRole.withName(info.role))))
    } yield info
  }
}
