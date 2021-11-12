package com.vyulabs.update.distribution.loaders

import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.directives.FutureDirectives
import akka.stream.Materializer
import com.mongodb.client.model.{Filters, Sorts, Updates}
import com.vyulabs.update.common.common.Common.DistributionId
import com.vyulabs.update.common.distribution.client.DistributionClient
import com.vyulabs.update.common.distribution.client.graphql.{GraphqlArgument, GraphqlMutation}
import com.vyulabs.update.common.distribution.server.DistributionDirectory
import com.vyulabs.update.distribution.client.AkkaHttpClient
import com.vyulabs.update.distribution.client.AkkaHttpClient.AkkaSource
import com.vyulabs.update.distribution.mongo.DatabaseCollections
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol._
import spray.json._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 11.11.20.
  * Copyright FanDate, Inc.
  */

class StateUploader(distribution: DistributionId, collections: DatabaseCollections,
                    distributionDirectory: DistributionDirectory,
                    client: DistributionClient[AkkaSource])
                   (implicit system: ActorSystem, materializer: Materializer, executionContext: ExecutionContext)  extends FutureDirectives with SprayJsonSupport { self =>
  private implicit val log = LoggerFactory.getLogger(this.getClass)

  val uploadInterval = FiniteDuration(10, TimeUnit.SECONDS)

  var task = Option.empty[Cancellable]

  def start(): Unit = {
    synchronized {
      task = Some(system.scheduler.scheduleOnce(uploadInterval)(uploadState()))
      log.debug("Uploader is scheduled")
    }
  }

  def stop(): Unit = {
    synchronized {
      for (task <- task) {
        if (task.cancel() || !task.isCancelled) {
          log.debug("Uploader is cancelled")
        } else {
          log.debug("Uploader failed to cancel")
        }
        this.task = None
      }
    }
  }

  private def uploadState(): Unit = {
    log.debug("Upload state")
    val result = for {
      _ <- uploadServiceStates().andThen {
        case Failure(ex) =>
          log.error("Upload service states error", ex)
      }
      _ <- uploadFaultReports().andThen {
        case Failure(ex) =>
          log.error("Upload fault reports error", ex)
      }
    } yield {}
    result.andThen {
      case result =>
        synchronized {
          for (_ <- task) {
            if (result.isSuccess) {
              log.debug(s"State is uploaded successfully")
            } else {
              log.error(s"State is failed to upload: ${result.failed.get}")
            }
            task = Some(system.scheduler.scheduleOnce(uploadInterval)(uploadState()))
            log.debug("Upload task is scheduled")
          }
        }
    }
  }

  private def uploadServiceStates(): Future[Unit] = {
    log.debug("Upload service states")
    for {
      from <- getLastUploadSequence(collections.State_ServiceStates.name)
      newStatesDocuments <- collections.State_ServiceStates.findSequenced(Filters.gt("_sequence", from), sort = Some(Sorts.ascending("_sequence")))
      newStates <- Future(newStatesDocuments)
    } yield {
      if (!newStates.isEmpty) {
        client.graphqlRequest(GraphqlMutation[Boolean]("setServiceStates", Seq(GraphqlArgument("state" -> newStates.map(_.document).toJson)))).
          andThen {
            case Success(_) =>
              setLastUploadSequence(collections.State_ServiceStates.name, newStatesDocuments.last.sequence)
            case Failure(ex) =>
              setLastUploadError(collections.State_ServiceStates.name, ex.getMessage)
          }
      } else {
        Promise[Unit].success(Unit).future
      }
    }
  }

  private def uploadFaultReports(): Future[Unit] = {
    log.debug("Upload fault reports")
    for {
      from <- getLastUploadSequence(collections.Faults_ReportsInfo.name)
      newReportsDocuments <- collections.Faults_ReportsInfo.findSequenced(Filters.gt("_sequence", from), sort = Some(Sorts.ascending("_sequence")))
      newReports <- Future(newReportsDocuments)
    } yield {
      if (!newReports.isEmpty) {
        Future.sequence(newReports.filter(_.document.distribution == distribution).map(report => {
          val file = distributionDirectory.getFaultReportFile(report.document.payload.id)
          val infoUpload = for {
            _ <- client.uploadFaultReport(report.document.payload.id, file)
            _ <- client.graphqlRequest(GraphqlMutation[Boolean]("addServiceFaultReportInfo", Seq(GraphqlArgument("fault" -> report.document.payload.toJson))))
          } yield {}
          infoUpload.
            andThen {
              case Success(_) =>
                setLastUploadSequence(collections.Faults_ReportsInfo.name, newReportsDocuments.last.sequence)
              case Failure(ex) =>
                setLastUploadError(collections.Faults_ReportsInfo.name, ex.getMessage)
            }
        }))
      } else {
        Promise[Unit].success(Unit).future
      }
    }
  }

  private def getLastUploadSequence(component: String): Future[Long] = {
    for {
      uploadStatus <- collections.State_UploadStatus
      sequence <- uploadStatus.find(Filters.eq("component", component)).map(_.headOption.map(_.payload.lastUploadSequence).flatten.getOrElse(-1L))
    } yield sequence
  }

  private def setLastUploadSequence(component: String, sequence: Long): Future[Boolean] = {
    for {
      uploadStatus <- collections.State_UploadStatus
      result <- uploadStatus.updateOne(Filters.eq("component", component),
        Updates.combine(Updates.set("payload.lastUploadSequence", sequence), Updates.unset("payload.lastError")))
        .map(r => r.getModifiedCount > 0)
    } yield result
  }

  private def setLastUploadError(component: String, error: String): Future[Unit] = {
    for {
      uploadStatus <- collections.State_UploadStatus
      _ <- uploadStatus.updateOne(Filters.eq("component", component),
        Updates.set("payload.lastError", error))
        .map(r => r.getModifiedCount > 0)
    } yield {}
  }
}

object StateUploader {
  def apply(distribution: DistributionId,
            collections: DatabaseCollections, distributionDirectory: DistributionDirectory,
            distributionUrl: String, accessToken: String)
           (implicit system: ActorSystem, materializer: Materializer, executionContext: ExecutionContext): StateUploader = {
    new StateUploader(distribution, collections, distributionDirectory,
      new DistributionClient(new AkkaHttpClient(distributionUrl, Some(accessToken))))
  }
}