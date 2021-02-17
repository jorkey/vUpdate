package com.vyulabs.update.builder

import com.vyulabs.update.builder.config.BuilderConfig
import com.vyulabs.update.common.common.{Arguments, ThreadTimer}
import com.vyulabs.update.common.distribution.client.{DistributionClient, HttpClientImpl, SyncDistributionClient}
import com.vyulabs.update.common.lock.SmartFilesLocker
import com.vyulabs.update.common.utils.Utils
import com.vyulabs.update.common.version.{ClientVersion, DeveloperDistributionVersion, DeveloperVersion}
import org.slf4j.LoggerFactory

import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 21.02.19.
  * Copyright FanDate, Inc.
  */
object BuilderMain extends App {
  implicit val log = LoggerFactory.getLogger(this.getClass)
  implicit val filesLocker = new SmartFilesLocker()
  implicit val executionContext = ExecutionContext.fromExecutor(null, ex => { ex.printStackTrace(); log.error("Uncatched exception", ex) })
  implicit val timer = new ThreadTimer()

  def usage(): String =
    "Use: <command> {[argument=value]}\n" +
    "  Commands:\n" +
    "    buildDistribution <cloudProvider=?> <distributionDirectory=?> <distributionName=?> <distributionTitle=?> <mongoDbName=?> <author=?> [sourceBranches==?[,?]...] [test=true]\n" +
    "    buildDistribution <cloudProvider=?> <distributionDirectory=?> <distributionName=?> <distributionTitle=?> <mongoDbName=?> <author=?> <developerDistributionUrL=?>\n" +
    "    buildDeveloperVersion <distributionName=?> <service=?> <version=?> [comment=?] [sourceBranches=?[,?]...]\n" +
    "    buildClientVersion <distributionName=?> <service=?> <developerVersion=?> <clientVersion=?>"

  if (args.size < 1) Utils.error(usage())

  val command = args(0)

  command match {
    case "buildDistribution" =>
      val arguments = Arguments.parse(args.drop(1), Set.empty)

      val cloudProvider = arguments.getValue("cloudProvider")
      val distributionDirectory = arguments.getValue("distributionDirectory")
      val distributionName = arguments.getValue("distributionName")
      val distributionTitle = arguments.getValue("distributionTitle")
      val mongoDbName = arguments.getValue("mongoDbName")
      val author = arguments.getValue("author")
      val port = arguments.getOptionIntValue("port").getOrElse(8000)
      val distributionBuilder = new DistributionBuilder(new File("."), cloudProvider,
        true, new File(distributionDirectory), distributionName, distributionTitle, mongoDbName, false, port)

      arguments.getOptionValue("developerDistributionUrl") match {
        case None =>
          if (!distributionBuilder.buildDistributionFromSources(author)) {
            Utils.error("Build distribution error")
          }
        case Some(developerDistributionUrl) =>
          if (!distributionBuilder.buildFromDeveloperDistribution(new URL(developerDistributionUrl), author)) {
            Utils.error("Build distribution error")
          }
      }
    case _ =>
      val arguments = Arguments.parse(args.drop(1), Set.empty)

      val config = BuilderConfig().getOrElse { Utils.error("No config") }
      val distributionName = arguments.getValue("distributionName")
      val distributionUrl = config.distributionLinks.find(_.distributionName == distributionName).map(_.distributionUrl).getOrElse {
        Utils.error(s"Unknown URL to distribution ${distributionName}")
      }
      val asyncDistributionClient = new DistributionClient(new HttpClientImpl(distributionUrl))
      val distributionClient = new SyncDistributionClient(asyncDistributionClient, FiniteDuration(60, TimeUnit.SECONDS))
      //TraceAppender.handleLogs(new LogSender(Common.DistributionServiceName, config.instanceId, asyncDistributionClient))

      command match {
        case "buildDeveloperVersion" =>
          val author = arguments.getValue("author")
          val serviceName = arguments.getValue("service")
          val version = DeveloperVersion.parse(arguments.getValue("version"))
          val comment: Option[String] = arguments.getOptionValue("comment")
          val sourceBranches = arguments.getOptionValue("sourceBranches").map(_.split(",").toSeq).getOrElse(Seq.empty)
          val developerBuilder = new DeveloperBuilder(new File("."), distributionName)
          if (!developerBuilder.buildDeveloperVersion(distributionClient, author, serviceName, version, comment, sourceBranches)) {
            Utils.error("Developer version is not generated")
          }
        case "buildClientVersion" =>
          val author = arguments.getValue("author")
          val serviceName = arguments.getValue("service")
          val developerVersion = DeveloperDistributionVersion.parse(arguments.getValue("developerVersion"))
          val clientVersion = ClientVersion.parse(arguments.getValue("clientVersion"))
          val buildArguments = Map("distribDirectoryUrl" -> distributionUrl.toString, "version" -> clientVersion.toString)
          val clientBuilder = new ClientBuilder(new File("."), distributionName)
          if (!clientBuilder.buildClientVersion(distributionClient, serviceName, developerVersion, clientVersion, author, buildArguments)) {
            Utils.error("Client version is not generated")
          }
      }
  }
}