package com.vyulabs.update.distribution

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.vyulabs.update.common.com.vyulabs.common.utils.Arguments
import com.vyulabs.update.distribution.client.ClientDistributionDirectory
import com.vyulabs.update.distribution.developer.DeveloperDistributionDirectory
import com.vyulabs.update.info.ClientFaultReport
import com.vyulabs.update.lock.SmartFilesLocker
import com.vyulabs.update.users.UsersCredentials.credentialsFile
import com.vyulabs.update.users.{PasswordHash, UserCredentials, UserRole, UsersCredentials}
import com.vyulabs.update.utils.{IoUtils, Utils}
import distribution.client.ClientDistribution
import distribution.client.config.ClientDistributionConfig
import distribution.client.uploaders.{ClientFaultUploader, ClientLogUploader, ClientStateUploader}
import distribution.developer.DeveloperDistribution
import distribution.developer.config.DeveloperDistributionConfig
import distribution.developer.uploaders.{DeveloperFaultUploader, DeveloperStateUploader}
import org.slf4j.LoggerFactory

import scala.io.StdIn
import com.vyulabs.update.users.UsersCredentials._
import distribution.client.graphql.ClientGraphQLSchema
import distribution.developer.graphql.DeveloperGraphQLSchema
import distribution.graphql.{GraphQL, GraphQLContext}
import distribution.mongo.MongoDb
import spray.json._

import scala.concurrent.ExecutionContext

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 19.04.19.
  * Copyright FanDate, Inc.
  */
object DistributionMain extends App {
  implicit val system = ActorSystem("Distribution")
  implicit val materializer = ActorMaterializer()

  implicit val log = LoggerFactory.getLogger(this.getClass)

  if (args.size < 1) {
    Utils.error(usage())
  }

  def usage() =
    "Arguments: developer <port=value>\n" +
    "           client <clientName=value> <developerDirectoryUrl=value> <port=value>\n" +
    "           addUser <userName=value> <role=value>\n" +
    "           removeUser <userName=value>\n" +
    "           changePassword <userName=value>"

  try {

    val command = args(0)
    val arguments = Arguments.parse(args.drop(1))

    implicit val executionContext = ExecutionContext.fromExecutor(null, ex => log.error("Uncatched exception", ex))

    implicit val filesLocker = new SmartFilesLocker()

    val usersCredentials = UsersCredentials()

    val mongoDb = new MongoDb("distribution")
    val graphqlContext = GraphQLContext(mongoDb)

    command match {
      case "developer" =>
        val config = DeveloperDistributionConfig().getOrElse {
          Utils.error("No config")
        }

        val dir = new DeveloperDistributionDirectory(new File(config.distributionDirectory))

        val stateUploader = new DeveloperStateUploader(dir)
        val faultUploader = new DeveloperFaultUploader(mongoDb.getCollection[ClientFaultReport]("faults"), dir)

        val selfDistributionDir = config.selfDistributionClient
          .map(client => new DistributionDirectory(dir.getClientDir(client))).getOrElse(dir)
        val selfUpdater = new SelfUpdater(selfDistributionDir)
        val graphql = new GraphQL(DeveloperGraphQLSchema.SchemaDefinition, graphqlContext)
        val distribution = new DeveloperDistribution(dir, config, usersCredentials, graphql, stateUploader, faultUploader)

        stateUploader.start()
        selfUpdater.start()

        Runtime.getRuntime.addShutdownHook(new Thread() {
          override def run(): Unit = {
            stateUploader.close()
            selfUpdater.close()
          }
        })

        distribution.run()

      case "client" =>
        val config = ClientDistributionConfig().getOrElse {
          Utils.error("No config")
        }

        val dir = new ClientDistributionDirectory(new File(config.distributionDirectory))

        val stateUploader = new ClientStateUploader(dir, config.developerDistributionUrl, config.instanceId, config.installerDirectory)
        val faultUploader = new ClientFaultUploader(dir, config.developerDistributionUrl)
        val logUploader = new ClientLogUploader(dir)

        val selfUpdater = new SelfUpdater(dir)
        val graphql = new GraphQL(ClientGraphQLSchema.SchemaDefinition, graphqlContext)
        val distribution = new ClientDistribution(dir, config, usersCredentials, graphql, stateUploader, logUploader, faultUploader)

        stateUploader.start()
        logUploader.start()
        faultUploader.start()
        selfUpdater.start()

        Runtime.getRuntime.addShutdownHook(new Thread() {
          override def run(): Unit = {
            stateUploader.close()
            logUploader.close()
            faultUploader.close()
            selfUpdater.close()
          }
        })

        distribution.run()

      case "addUser" =>
        val userName = arguments.getValue("userName")
        val role = UserRole.withName(arguments.getValue("role"))
        val password = StdIn.readLine("Enter password: ")
        if (usersCredentials.getCredentials(userName).isDefined) {
          Utils.error(s"User ${userName} credentials already exists")
        }
        usersCredentials.addUser(userName, UserCredentials(role, PasswordHash(password)))
        if (!IoUtils.writeJsonToFile(credentialsFile, usersCredentials.toJson)) {
          Utils.error("Can't save credentials file")
        }
        sys.exit()

      case "removeUser" =>
        val userName = arguments.getValue("userName")
        usersCredentials.removeUser(userName)
        if (!IoUtils.writeJsonToFile(credentialsFile, usersCredentials.toJson)) {
          Utils.error("Can't save credentials file")
        }
        sys.exit()

      case "changePassword" =>
        val userName = arguments.getValue("userName")
        val password = StdIn.readLine("Enter password: ")
        usersCredentials.getCredentials(userName) match {
          case Some(credentials) =>
            credentials.password = PasswordHash(password)
            if (!IoUtils.writeJsonToFile(credentialsFile, usersCredentials.toJson)) {
              Utils.error("Can't save credentials file")
            }
          case None =>
            Utils.error(s"No user ${userName} credentials")
        }
        sys.exit()

      case _ =>
        Utils.error(s"Invalid command ${command}\n${usage()}")
    }
  } catch {
    case ex: Throwable =>
      log.error("Exception", ex)
      Utils.error(ex.getMessage)
      sys.exit(1)
  }
}