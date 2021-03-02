package com.vyulabs.update.tests

import java.io.File
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.TimeUnit

import com.vyulabs.update.builder.config.{SourceConfig, SourcesConfig}
import com.vyulabs.update.builder.{ClientBuilder, DistributionBuilder}
import com.vyulabs.update.common.common.Common
import com.vyulabs.update.common.common.Common.TaskId
import com.vyulabs.update.common.config._
import com.vyulabs.update.common.distribution.client.graphql.AdministratorGraphqlCoder.{administratorMutations, administratorQueries, administratorSubscriptions}
import com.vyulabs.update.common.distribution.client.{DistributionClient, HttpClientImpl, SyncDistributionClient, SyncSource}
import com.vyulabs.update.common.distribution.server.{DistributionDirectory, SettingsDirectory}
import com.vyulabs.update.common.info.{ClientDesiredVersionDelta, UserRole}
import com.vyulabs.update.common.process.ChildProcess
import com.vyulabs.update.common.utils.IoUtils
import com.vyulabs.update.common.version.{ClientDistributionVersion, ClientVersion, DeveloperDistributionVersion, DeveloperVersion}
import com.vyulabs.update.distribution.mongo.MongoDb
import com.vyulabs.update.updater.config.UpdaterConfig
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext}

class SimpleLifecycle {
  private implicit val executionContext = ExecutionContext.fromExecutor(null, ex => { ex.printStackTrace(); log.error("Uncatched exception", ex) })
  private implicit val log = LoggerFactory.getLogger(this.getClass)

  private val distributionName = "test-distribution"
  private val distributionDir = Files.createTempDirectory("distribution").toFile
  private val builderDir = new File(distributionDir, "builder")
  private val settingsDirectory = new SettingsDirectory(builderDir, distributionName)
  private val adminDistributionUrl = new URL("http://admin:admin@localhost:8000")
  private val serviceDistributionUrl = new URL("http://service:service@localhost:8000")
  private val testServiceName = "test"
  private val testServiceSourcesDir = Files.createTempDirectory("service-sources").toFile
  private val testServiceInstanceDir = Files.createTempDirectory("service-instance").toFile

  private val distributionBuilder = new DistributionBuilder("None", false,
    new DistributionDirectory(distributionDir), distributionName, "Test distribution server", "test", false, 8000)
  private val clientBuilder = new ClientBuilder(builderDir, distributionName)

  private val distributionClient = new SyncDistributionClient(
    new DistributionClient(new HttpClientImpl(adminDistributionUrl)), FiniteDuration(60, TimeUnit.SECONDS))

  Await.result(new MongoDb("test").dropDatabase(), FiniteDuration(5, TimeUnit.SECONDS))

  def makeAndRunDistribution(): Unit = {
    println()
    println("************************************** Make and run distribution")
    println()
    println("====================================== Setup and start distribution server")
    println()
    if (!distributionBuilder.buildDistributionFromSources("ak")) {
      sys.error("Can't build distribution server")
    }

    println()
    println("====================================== Add service user to distribution server")
    println()
    if (!distributionClient.graphqlRequest(administratorMutations.addUser("service", UserRole.Service, "service")).getOrElse(false)) {
      sys.error("Can't add service user")
    }

    println()
    println(s"====================================== Configure test service in directory ${testServiceSourcesDir}")
    println()
    val buildConfig = BuildConfig(None, Seq(CopyFileConfig("sourceScript.sh", "runScript.sh", None, Some(Map.empty + ("version" -> "%%version%%")))))
    val installCommands = Seq(CommandConfig("chmod", Some(Seq("+x", "runScript.sh")), None, None, None, None))
    val logWriter = LogWriterConfig("log", "test", 1, 10)
    val installConfig = InstallConfig(Some(installCommands), None, Some(RunServiceConfig("/bin/sh", Some(Seq("-c", "./runScript.sh")),
      None, Some(logWriter), Some(false), None, None, None)))
    val updateConfig = UpdateConfig(Map.empty + (testServiceName -> ServiceUpdateConfig(buildConfig, Some(installConfig))))
    if (!IoUtils.writeJsonToFile(new File(testServiceSourcesDir, Common.UpdateConfigFileName), updateConfig)) {
      sys.error(s"Can't write update config file")
    }
    val scriptContent = "echo \"Executed version %%version%%\""
    if (!IoUtils.writeBytesToFile(new File(testServiceSourcesDir, "sourceScript.sh"), scriptContent.getBytes("utf8"))) {
      sys.error(s"Can't write script")
    }
    val sourcesConfig = IoUtils.readFileToJson[SourcesConfig](settingsDirectory.getSourcesFile()).getOrElse {
      sys.error(s"Can't read sources config file")
    }
    val newSourcesConfig = SourcesConfig(sourcesConfig.sources + (testServiceName -> Seq(SourceConfig(Left(testServiceSourcesDir.getAbsolutePath), None))))
    if (!IoUtils.writeJsonToFile(settingsDirectory.getSourcesFile(), newSourcesConfig)) {
      sys.error(s"Can't write sources config file")
    }

    println()
    println(s"====================================== Make test service version")
    buildTestServiceVersions(distributionClient, DeveloperVersion.initialVersion)

    println()
    println(s"====================================== Setup and start updater with test service in directory ${testServiceInstanceDir}")
    println()
    if (!IoUtils.copyFile(new File("./scripts/updater/updater.sh"), new File(testServiceInstanceDir, "updater.sh")) ||
      !IoUtils.copyFile(new File("./scripts/.update.sh"), new File(testServiceInstanceDir, ".update.sh"))) {
      sys.error("Copying of updater scripts error")
    }
    val updaterConfig = UpdaterConfig("Test", serviceDistributionUrl)
    if (!IoUtils.writeJsonToFile(new File(testServiceInstanceDir, Common.UpdaterConfigFileName), updaterConfig)) {
      sys.error(s"Can't write ${Common.UpdaterConfigFileName}")
    }
    val process = Await.result(
      ChildProcess.start("/bin/sh", Seq("./updater.sh", "runServices", s"services=${testServiceName}"),
        Map.empty, testServiceInstanceDir, lines => lines.foreach(line => println(s"Updater: ${line._1}"))), FiniteDuration(15, TimeUnit.SECONDS))
    process.onTermination().map(status => println(s"Updater is terminated with status ${status}"))
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        Await.result(process.terminate(), FiniteDuration(3, TimeUnit.SECONDS))
      }
    })


    println()
    println(s"************************************** Distribution server is ready")
  }

  def updateTestService(): Unit = {
    println()
    println(s"************************************** Fix test service in directory ${testServiceInstanceDir}")
    println()
    val fixedScriptContent = "echo \"Executed version %%version%%\"\nsleep 10000\n"
    if (!IoUtils.writeBytesToFile(new File(testServiceSourcesDir, "sourceScript.sh"), fixedScriptContent.getBytes("utf8"))) {
      sys.error(s"Can't write script")
    }

    println()
    println(s"====================================== Make fixed test service version")
    buildTestServiceVersions(distributionClient, DeveloperVersion.initialVersion.next())

    println()
    println(s"************************************** Test service is fixed")
  }

  def updateDistribution(): Unit = {
    println(s"************************************** Upload new client version of distribution")
    println()
    val newDistributionVersion = ClientDistributionVersion(distributionName, ClientVersion(DeveloperVersion.initialVersion, Some(1)))
    if (!clientBuilder.uploadClientVersion(distributionClient, Common.DistributionServiceName, newDistributionVersion, "ak")) {
      sys.error(s"Can't write script")
    }
    if (!clientBuilder.setDesiredVersions(distributionClient, Seq(ClientDesiredVersionDelta(Common.DistributionServiceName, Some(newDistributionVersion))))) {
      sys.error("Set distribution desired version error")
    }

    println()
    println(s"====================================== Wait for distribution server updated")
    println()
    Thread.sleep(5000)
    distributionBuilder.waitForServerAvailable(distributionClient)
    val states = distributionClient.graphqlRequest(administratorQueries.getServiceStates(distributionName = Some(distributionName),
      serviceName = Some(Common.DistributionServiceName))).getOrElse {
      sys.error("Can't get version of distribution server")
    }
    assert(Some(newDistributionVersion) == states.head.instance.service.version)

    println()
    println(s"************************************** Distribution is updated")
  }

  private def buildTestServiceVersions(distributionClient: SyncDistributionClient[SyncSource], version: DeveloperVersion): Unit = {
    println()
    println("====================================== Build developer version of test service")
    println()
    val taskId = distributionClient.graphqlRequest(administratorMutations.buildDeveloperVersion(testServiceName, version)).getOrElse {
      sys.error("Can't execute build developer version task")
    }
    if (!subscribeTask(distributionClient, taskId)) {
      sys.error("Execution of build developer version task error")
    }

    println()
    println("====================================== Build client version of test service")
    println()
    val taskId1 = distributionClient.graphqlRequest(administratorMutations.buildClientVersion(testServiceName,
      DeveloperDistributionVersion(distributionName, version),
      ClientDistributionVersion(distributionName, ClientVersion(version)))).getOrElse {
      sys.error("Can't execute build client version task")
    }
    if (!subscribeTask(distributionClient, taskId1)) {
      sys.error("Execution of build client version task error")
    }

    println()
    println("====================================== Set client desired versions")
    println()
    if (!distributionClient.graphqlRequest(administratorMutations.setClientDesiredVersions(Seq(
        ClientDesiredVersionDelta(testServiceName, Some(ClientDistributionVersion(distributionName, ClientVersion(version))))))).getOrElse(false)) {
      log.error("Set client desired versions error")
      return false
    }
  }

  private def subscribeTask(distributionClient: SyncDistributionClient[SyncSource], taskId: TaskId): Boolean = {
    val source = distributionClient.graphqlSubRequest(administratorSubscriptions.subscribeTaskLogs(taskId)).getOrElse {
      sys.error("Can't subscribe build developer version task")
    }
    while (true) {
      val log = source.next().getOrElse {
        sys.error("Unexpected end of subscription")
      }
      //println(log.logLine.line.message)
      for (terminationStatus <- log.logLine.line.terminationStatus) {
        println(s"Build developer version termination status is ${terminationStatus}")
        return terminationStatus
      }
    }
    false
  }
}