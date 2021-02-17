package com.vyulabs.update.distribution.http.client

import akka.http.scaladsl.Http
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.vyulabs.update.common.common.Common
import com.vyulabs.update.common.config.{DistributionClientConfig, DistributionClientInfo}
import com.vyulabs.update.common.distribution.client.graphql.AdministratorGraphqlCoder._
import com.vyulabs.update.common.distribution.client.graphql.DistributionGraphqlCoder.{distributionMutations, distributionQueries}
import com.vyulabs.update.common.distribution.client.graphql.ServiceGraphqlCoder._
import com.vyulabs.update.common.distribution.client.{DistributionClient, HttpClientImpl, SyncDistributionClient}
import com.vyulabs.update.common.info._
import com.vyulabs.update.common.version.{ClientDistributionVersion, ClientVersion, DeveloperDistributionVersion, DeveloperVersion}
import com.vyulabs.update.distribution.TestEnvironment
import com.vyulabs.update.distribution.client.AkkaHttpClient
import spray.json.DefaultJsonProtocol._

import java.net.URL
import java.util.Date
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 23.11.20.
  * Copyright FanDate, Inc.
  */
class RequestsTest extends TestEnvironment with ScalatestRouteTest {
  behavior of "Own distribution client graphql requests"

  val route = distribution.route

  var server = Http().newServerAt("0.0.0.0", 8081).adaptSettings(s => s.withTransparentHeadRequests(true))
  server.bind(route)

  val stateDate = new Date()

  override def dbName = super.dbName + "-client"

  override def beforeAll() = {
    val serviceStatesCollection = collections.State_ServiceStates
    val clientInfoCollection = collections.Developer_DistributionClientsInfo

    result(clientInfoCollection.insert(DistributionClientInfo("distribution1", DistributionClientConfig("common", None))))

    result(serviceStatesCollection.insert(
      DistributionServiceState(distributionName, "instance1", DirectoryServiceState("distribution", "directory1",
        ServiceState(date = stateDate, None, None, version =
          Some(ClientDistributionVersion(distributionName, ClientVersion(DeveloperVersion(Seq(1, 2, 3))))), None, None, None, None)))))
  }

  def httpRequests(): Unit = {
    val adminClient = new SyncDistributionClient(
      new DistributionClient(new HttpClientImpl(new URL("http://admin:admin@localhost:8081"))), FiniteDuration(15, TimeUnit.SECONDS))
    val serviceClient = new SyncDistributionClient(
      new DistributionClient(new HttpClientImpl(new URL("http://service1:service1@localhost:8081"))), FiniteDuration(15, TimeUnit.SECONDS))
    val distribClient = new SyncDistributionClient(
      new DistributionClient(new HttpClientImpl(new URL("http://distribution1:distribution1@localhost:8081"))), FiniteDuration(15, TimeUnit.SECONDS))
    requests(adminClient, serviceClient, distribClient)
  }

  def akkaHttpRequests(): Unit = {
    val adminClient = new SyncDistributionClient(
      new DistributionClient(new AkkaHttpClient(new URL("http://admin:admin@localhost:8081"))), FiniteDuration(15, TimeUnit.SECONDS))
    val serviceClient = new SyncDistributionClient(
      new DistributionClient(new AkkaHttpClient(new URL("http://service1:service1@localhost:8081"))), FiniteDuration(15, TimeUnit.SECONDS))
    val distribClient = new SyncDistributionClient(
      new DistributionClient(new AkkaHttpClient(new URL("http://distribution1:distribution1@localhost:8081"))), FiniteDuration(15, TimeUnit.SECONDS))
    requests(adminClient, serviceClient, distribClient)
  }

  def requests[Source[_]](adminClient: SyncDistributionClient[Source], serviceClient: SyncDistributionClient[Source], distribClient: SyncDistributionClient[Source]): Unit = {
    it should "execute some requests" in {
      assertResult(Some(ClientDistributionVersion.parse("test-1.2.3")))(adminClient.getServiceVersion(distributionName, Common.DistributionServiceName))

      assertResult(Some(Seq(DistributionClientInfo("distribution1", DistributionClientConfig("common", None)))))(
        adminClient.graphqlRequest(administratorQueries.getDistributionClientsInfo()))

      assertResult(Some(DistributionClientConfig("common", None)))(
        distribClient.graphqlRequest(distributionQueries.getDistributionClientConfig()))
    }

    it should "process request errors" in {
      assertResult(None)(serviceClient.graphqlRequest(administratorQueries.getDistributionClientsInfo()))
      assertResult(None)(distribClient.graphqlRequest(administratorQueries.getDistributionClientsInfo()))
    }

    it should "execute developer version requests" in {
      val date = new Date()

      assert(adminClient.graphqlRequest(administratorMutations.addDeveloperVersionInfo(
        DeveloperVersionInfo("service1", DeveloperDistributionVersion.parse("test-1.2.3"),
          BuildInfo("author1", Seq("master"), date, Some("comment"))))).getOrElse(false))

      assertResult(Some(Seq(DeveloperVersionInfo("service1", DeveloperDistributionVersion.parse("test-1.2.3"),
        BuildInfo("author1", Seq("master"), date, Some("comment"))))))(
        adminClient.graphqlRequest(administratorQueries.getDeveloperVersionsInfo("service1", None,
          Some(DeveloperDistributionVersion.parse("test-1.2.3")))))

      assert(adminClient.graphqlRequest(administratorMutations.removeDeveloperVersion("service1",
        DeveloperDistributionVersion.parse("test-1.2.3"))).getOrElse(false))

      assert(adminClient.graphqlRequest(
        administratorMutations.setDeveloperDesiredVersions(Seq(DeveloperDesiredVersion("service1", DeveloperDistributionVersion.parse("test-1.2.3")))))
        .getOrElse(false))

      assertResult(Some(Seq(DeveloperDesiredVersion("service1", DeveloperDistributionVersion.parse("test-1.2.3")))))(adminClient.graphqlRequest(
        administratorQueries.getDeveloperDesiredVersions(Seq("service1"))))

      assertResult(Some(Seq(DeveloperDesiredVersion("service1", DeveloperDistributionVersion.parse("test-1.2.3")))))(
        distribClient.graphqlRequest(distributionQueries.getDeveloperDesiredVersions(Seq("service1"))))
    }

    it should "execute client version requests" in {
      val date = new Date()

      assert(adminClient.graphqlRequest(administratorMutations.addClientVersionInfo(
        ClientVersionInfo("service1", ClientDistributionVersion.parse("test-1.2.3_1"),
          BuildInfo("author1", Seq("master"), date, Some("comment")), InstallInfo("user1", date)))).getOrElse(false))

      assertResult(Some(Seq(ClientVersionInfo("service1", ClientDistributionVersion.parse("test-1.2.3_1"),
        BuildInfo("author1", Seq("master"), date, Some("comment")), InstallInfo("user1", date)))))(
        adminClient.graphqlRequest(administratorQueries.getClientVersionsInfo("service1", None,
          Some(ClientDistributionVersion.parse("test-1.2.3_1")))))

      assert(adminClient.graphqlRequest(administratorMutations.removeClientVersion("service1",
        ClientDistributionVersion.parse("test-1.2.3_1"))).getOrElse(false))

      assert(adminClient.graphqlRequest(
        administratorMutations.setClientDesiredVersions(Seq(ClientDesiredVersion("service1", ClientDistributionVersion.parse("test-1.2.3_1"))))).getOrElse(false))

      assertResult(Some(Seq(ClientDesiredVersion("service1", ClientDistributionVersion.parse("test-1.2.3_1")))))(adminClient.graphqlRequest(
        administratorQueries.getClientDesiredVersions(Seq("service1"))))

      assertResult(Some(List(ClientDesiredVersion("service1", ClientDistributionVersion.parse("test-1.2.3_1")))))(
        serviceClient.graphqlRequest(serviceQueries.getClientDesiredVersions(Seq("service1"))))
    }

    it should "execute installed versions requests" in {
      assert(distribClient.graphqlRequest(
        distributionMutations.setInstalledDesiredVersions(Seq(ClientDesiredVersion("service1", ClientDistributionVersion.parse("test-1.1.1"))))).getOrElse(false))

      assertResult(Some(Seq(ClientDesiredVersion("service1", ClientDistributionVersion.parse("test-1.1.1")))))(adminClient.graphqlRequest(
        administratorQueries.getInstalledDesiredVersions("distribution1", Seq("service1"))))
    }

    it should "execute tested versions requests" in {
      distribClient.graphqlRequest(distributionMutations.setTestedVersions(Seq(DeveloperDesiredVersion("service1", DeveloperDistributionVersion.parse("test-1.2.3")))))
    }

    it should "execute service states requests" in {
      assert(serviceClient.graphqlRequest(serviceMutations.setServiceStates(Seq(InstanceServiceState("instance1", "service1", "directory1",
        ServiceState(stateDate, None, None, Some(ClientDistributionVersion.parse(s"${distributionName}-1.2.1")), None, None, None, None))))).getOrElse(false))

      assert(distribClient.graphqlRequest(distributionMutations.setServiceStates(Seq(InstanceServiceState("instance1", "service1", "directory1",
        ServiceState(stateDate, None, None, Some(ClientDistributionVersion.parse(s"distribution1-3.2.1")), None, None, None, None))))).getOrElse(false))

      assertResult(Some(Seq(DistributionServiceState(distributionName, InstanceServiceState("instance1", "service1", "directory1",
        ServiceState(stateDate, None, None, Some(ClientDistributionVersion.parse(s"${distributionName}-1.2.1")), None, None, None, None))))))(
        adminClient.graphqlRequest(administratorQueries.getServiceStates(Some(distributionName), Some("service1"), Some("instance1"), Some("directory1"))))

      assertResult(Some(Seq(DistributionServiceState("distribution1", InstanceServiceState("instance1", "service1", "directory1",
        ServiceState(stateDate, None, None, Some(ClientDistributionVersion.parse(s"distribution1-3.2.1")), None, None, None, None))))))(
        adminClient.graphqlRequest(administratorQueries.getServiceStates(Some("distribution1"), Some("service1"), Some("instance1"), Some("directory1"))))
    }

    it should "execute service log requests" in {
      assert(serviceClient.graphqlRequest(serviceMutations.addServiceLogs("service1", "instance1", "process1", None, "directory1",
        Seq(LogLine(new Date(), "INFO", None, "log line", None)))).getOrElse(false))
    }

    it should "execute fault report requests" in {
      assert(serviceClient.graphqlRequest(serviceMutations.addFaultReportInfo(ServiceFaultReport("fault1", FaultInfo(stateDate, "instance1", "directory", "service1", "common",
        ServiceState(stateDate, None, None, None, None, None, None, None), Seq()), Seq("fault1.info", "core")))).getOrElse(false))

      assert(distribClient.graphqlRequest(distributionMutations.addFaultReportInfo(ServiceFaultReport("fault2", FaultInfo(stateDate, "instance1", "directory", "service1", "common",
        ServiceState(stateDate, None, None, None, None, None, None, None), Seq()), Seq("fault2.info", "core")))).getOrElse(false))

      assertResult(Some(Seq(DistributionFaultReport(distributionName, ServiceFaultReport("fault1", FaultInfo(stateDate, "instance1", "directory", "service1", "common",
        ServiceState(stateDate, None, None, None, None, None, None, None), Seq()), Seq("fault1.info", "core"))))))(
        adminClient.graphqlRequest(administratorQueries.getFaultReportsInfo(Some(distributionName), Some("service1"), Some(2))))

      assertResult(Some(Seq(DistributionFaultReport("distribution1", ServiceFaultReport("fault2", FaultInfo(stateDate, "instance1", "directory", "service1", "common",
        ServiceState(stateDate, None, None, None, None, None, None, None), Seq()), Seq("fault2.info", "core"))))))(
        adminClient.graphqlRequest(administratorQueries.getFaultReportsInfo(Some("distribution1"), Some("service1"), Some(2))))

      result(collections.State_FaultReportsInfo.drop())
    }
  }

  def subRequests(): Unit = {
    val adminClient = new SyncDistributionClient(
      new DistributionClient(new HttpClientImpl(new URL("http://admin:admin@localhost:8081"))), FiniteDuration(15, TimeUnit.SECONDS))

    it should "execute subscription requests" in {
      val source = adminClient.graphqlSubRequest(administratorSubscriptions.testSubscription())
      var line = Option.empty[String]
      do {
        line = source.get.next()
        line.foreach(println(_))
      } while (line.isDefined)
    }
  }

  def akkaSubRequests(): Unit = {
    val adminClient = new SyncDistributionClient(
      new DistributionClient(new AkkaHttpClient(new URL("http://admin:admin@localhost:8081"))), FiniteDuration(15, TimeUnit.SECONDS))

    it should "execute subscription requests" in {
      val source = adminClient.graphqlSubRequest(administratorSubscriptions.testSubscription()).get
      result(source.map(println(_)).run())
    }
  }

  "Http requests" should behave like httpRequests()
  "Akka http requests" should behave like akkaHttpRequests()

  "Http subscription requests" should behave like subRequests()
  "Akka http subscription requests" should behave like akkaSubRequests()
}