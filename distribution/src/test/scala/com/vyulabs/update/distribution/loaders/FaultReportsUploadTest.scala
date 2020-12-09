package com.vyulabs.update.distribution.loaders

import java.io.{File, IOException}
import java.util.Date
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.mongodb.client.model.Filters
import com.vyulabs.update.common.Common.FaultId
import com.vyulabs.update.distribution.TestEnvironment
import com.vyulabs.update.info.{DistributionFaultReport, FaultInfo, ServiceFaultReport, ServiceState}
import com.vyulabs.update.version.{ClientDistributionVersion, ClientVersion, DeveloperVersion}
import distribution.loaders.StateUploader
import distribution.mongo.{FaultReportDocument, UploadStatus, UploadStatusDocument}
import com.vyulabs.update.distribution.DistributionWebPaths._
import com.vyulabs.update.distribution.client.{DistributionClient, HttpClient, HttpClientTestStub}
import com.vyulabs.update.distribution.client.graphql.{GraphqlArgument, GraphqlMutation, GraphqlRequest}

import scala.concurrent.{ExecutionContext, Future, Promise}
import spray.json._
import spray.json.DefaultJsonProtocol._

class FaultReportsUploadTest extends TestEnvironment {
  behavior of "Fault Reports Upload"

  implicit val system = ActorSystem("Distribution")
  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(null, ex => { ex.printStackTrace(); log.error("Uncatched exception", ex) })

  val date = new Date()

  val httpClient = new HttpClientTestStub()
  val distributionClient = new DistributionClient(distributionName, httpClient)

  it should "upload fault reports" in {
    val uploader = new StateUploader(distributionName, collections, distributionDir, 1, distributionClient)
    uploader.start()

    val report = DistributionFaultReport(distributionName, ServiceFaultReport("fault1", FaultInfo(new Date(), "instance1", "directory", "service1", "profile1",
      ServiceState(new Date(), None, None, version = Some(ClientDistributionVersion("test", ClientVersion(DeveloperVersion(Seq(1))))), None, None, None, None), Seq()), Seq("file1")))
    result(collections.State_FaultReportsInfo.map(_.insert(FaultReportDocument(0, report))).flatten)
    waitForFaultReportUpload("fault1").success()
    waitForAddServiceFaultReportInfo(report).success(true)

    Thread.sleep(100)
    uploader.stop()

    result(collections.State_FaultReportsInfo.map(_.dropItems()).flatten)
    result(collections.State_UploadStatus.map(_.dropItems()).flatten)
  }

  it should "try to upload service states again after failure" in {
    val uploader = new StateUploader(distributionName, collections, distributionDir, 2, distributionClient)
    uploader.start()

    val report1 = DistributionFaultReport(distributionName, ServiceFaultReport("fault1", FaultInfo(new Date(), "instance1", "directory", "service1", "profile1",
      ServiceState(new Date(), None, None, version = Some(ClientDistributionVersion("test", ClientVersion(DeveloperVersion(Seq(1))))), None, None, None, None), Seq()), Seq("file1")))
    result(collections.State_FaultReportsInfo.map(_.insert(FaultReportDocument(0, report1))).flatten)
    waitForFaultReportUpload( "fault1").failure(new IOException("upload error"))

    Thread.sleep(100)
    assertResult(UploadStatusDocument("state.faultReportsInfo", UploadStatus(None, Some("upload error"))))(
      result(result(collections.State_UploadStatus.map(_.find(Filters.eq("component", "state.faultReportsInfo")).map(_.head)))))

    waitForFaultReportUpload( "fault1").success()
    waitForAddServiceFaultReportInfo(report1).success(true)

    val report2 = DistributionFaultReport(distributionName, ServiceFaultReport("fault2", FaultInfo(new Date(), "instance2", "directory", "service2", "profile2",
      ServiceState(new Date(), None, None, version = Some(ClientDistributionVersion("test", ClientVersion(DeveloperVersion(Seq(2))))), None, None, None, None), Seq()), Seq("file2")))
    result(collections.State_FaultReportsInfo.map(_.insert(FaultReportDocument(1, report2))))
    waitForFaultReportUpload( "fault2").success()
    waitForAddServiceFaultReportInfo(report2).success(true)

    uploader.stop()

    result(collections.State_FaultReportsInfo.map(_.dropItems()).flatten)
    result(collections.State_UploadStatus.map(_.dropItems()).flatten)
  }

  def waitForFaultReportUpload(faultId: FaultId): Promise[Unit] = {
    httpClient.waitForUpload(faultReportPath + "/" + faultId, "fault-report")
  }

  def waitForAddServiceFaultReportInfo(report: DistributionFaultReport): Promise[Boolean] = {
    httpClient.waitForMutation("addServiceFaultReportInfo", Seq(GraphqlArgument("fault" -> report.report.toJson)))
  }
}