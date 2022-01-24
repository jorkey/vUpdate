package com.vyulabs.update.distribution.graphql.utils

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.Materializer
import com.mongodb.client.model.Filters
import com.vyulabs.update.common.common.Common.{DistributionId, ServiceId}
import com.vyulabs.update.common.config.{BuildServiceConfig, DistributionConfig, NamedStringValue, Repository}
import com.vyulabs.update.distribution.mongo.DatabaseCollections
import org.bson.BsonDocument
import org.slf4j.Logger

import java.io.IOException
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters.asJavaIterableConverter

trait ConfigBuilderUtils extends SprayJsonSupport {
  protected implicit val system: ActorSystem
  protected implicit val materializer: Materializer
  protected implicit val executionContext: ExecutionContext

  protected val config: DistributionConfig
  protected val collections: DatabaseCollections

  def setBuildDeveloperServiceConfig(service: ServiceId,
                                     distribution: Option[DistributionId],
                                     environment: Seq[NamedStringValue],
                                     sourceRepositories: Seq[Repository],
                                     privateFiles: Seq[String],
                                     macroValues: Seq[NamedStringValue])
                                    (implicit log: Logger): Future[Boolean] = {
    log.info(if (!service.isEmpty) s"Set developer service ${service} config" else "Set common developer services config")
    val filters = Filters.eq("service", service)
    collections.Developer_BuildServices.update(filters, _ =>
      Some(BuildServiceConfig(service, distribution, environment, sourceRepositories, privateFiles, macroValues))).map(_ > 0)
  }

  def removeBuildDeveloperServiceConfig(service: ServiceId)(implicit log: Logger): Future[Boolean] = {
    log.info(if (!service.isEmpty) s"Remove developer service ${service} config" else "Remove common developer services config")
    val filters = Filters.eq("service", service)
    collections.Developer_BuildServices.delete(filters).map(_ > 0)
  }

  def getBuildDeveloperServicesConfig(service: Option[ServiceId])
                                     (implicit log: Logger): Future[Seq[BuildServiceConfig]] = {
    var args = service.map(Filters.eq("service", _)).toSeq
    if (!service.contains("")) {
      args :+= Filters.ne("service", "")
    }
    val filters = if (!args.isEmpty) Filters.and(args.asJava) else new BsonDocument()
    collections.Developer_BuildServices.find(filters)
  }

  def getBuildDeveloperServiceConfig(service: ServiceId)
                                    (implicit log: Logger): Future[BuildServiceConfig] = {
    getBuildDeveloperServicesConfig(Some(service)).map(_.headOption.getOrElse {
      throw new IOException(if (!service.isEmpty) s"No developer service ${service} config" else "No common developer services config")
    })
  }

  def setBuildClientServiceConfig(service: ServiceId,
                                  distribution: Option[DistributionId],
                                  environment: Seq[NamedStringValue],
                                  settingsRepositories: Seq[Repository],
                                  privateFiles: Seq[String],
                                  macroValues: Seq[NamedStringValue])
                                  (implicit log: Logger): Future[Boolean] = {
    log.info(if (!service.isEmpty) s"Set client service ${service} config" else "Set common client services config")
    val filters = Filters.eq("service", service)
    collections.Client_BuildServices.update(filters, _ =>
      Some(BuildServiceConfig(service, distribution, environment, settingsRepositories,
        privateFiles, macroValues))).map(_ > 0)
  }

  def removeBuildClientServiceConfig(service: ServiceId)(implicit log: Logger): Future[Boolean] = {
    log.info(if (!service.isEmpty) s"Remove client service ${service} config" else "Remove common client services config")
    val filters = Filters.eq("service", service)
    collections.Client_BuildServices.delete(filters).map(_ > 0)
  }

  def getBuildClientServiceConfig(service: ServiceId)(implicit log: Logger): Future[Option[BuildServiceConfig]] = {
    getBuildClientServicesConfig(Some(service)).map(_.headOption)
  }

  def getBuildClientServicesConfig(service: Option[ServiceId])
                                  (implicit log: Logger): Future[Seq[BuildServiceConfig]] = {
    var args = service.map(Filters.eq("service", _)).toSeq
    if (!service.contains("")) {
      args :+= Filters.ne("service", "")
    }
    val filters = if (!args.isEmpty) Filters.and(args.asJava) else new BsonDocument()
    collections.Client_BuildServices.find(filters)
  }
}
