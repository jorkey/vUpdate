package com.vyulabs.update.distribution.mongo

import com.mongodb.MongoClientSettings
import com.mongodb.client.model._
import com.vyulabs.update.common.config.{DistributionClientConfig, DistributionClientInfo, DistributionClientProfile}
import com.vyulabs.update.common.info._
import com.vyulabs.update.common.version.{ClientDistributionVersion, ClientVersion, DeveloperDistributionVersion, DeveloperVersion}
import com.vyulabs.update.distribution.users.{PasswordHash, ServerUserInfo, UserCredentials}
import org.bson.BsonDocument
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.IterableCodecProvider
import org.mongodb.scala.bson.codecs.Macros._
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

class DatabaseCollections(db: MongoDb, instanceStateExpireSec: Int)(implicit executionContext: ExecutionContext) {
  private implicit val log = LoggerFactory.getLogger(this.getClass)

  private implicit def codecRegistry = fromRegistries(fromProviders(MongoClientSettings.getDefaultCodecRegistry(),
    IterableCodecProvider.apply,
    classOf[SequenceDocument],
    classOf[ServerUserInfo],
    classOf[UserCredentials],
    classOf[PasswordHash],
    classOf[ClientVersion],
    classOf[ClientDistributionVersion],
    classOf[DeveloperVersion],
    classOf[DeveloperDistributionVersion],
    classOf[BuildInfo],
    classOf[ClientVersionInfo],
    classOf[DeveloperDesiredVersion],
    classOf[DeveloperVersionInfo],
    classOf[ClientDesiredVersion],
    classOf[InstalledDesiredVersions],
    classOf[InstallInfo],
    classOf[DistributionClientProfile],
    classOf[DistributionClientConfig],
    classOf[DistributionClientInfo],
    classOf[DistributionServiceState],
    classOf[InstanceServiceState],
    classOf[ServiceState],
    classOf[ServiceLogLine],
    classOf[DistributionFaultReport],
    classOf[TestedDesiredVersions],
    classOf[TestSignature],
    classOf[LogLine],
    classOf[FaultInfo],
    classOf[UploadStatus],
    classOf[UploadStatusDocument],
    classOf[ServiceFaultReport]))

  val Sequences = for {
    collection <- db.getOrCreateCollection[SequenceDocument]("_.sequences")
    _ <- collection.createIndex(Indexes.ascending("name"), new IndexOptions().unique(true))
  } yield collection

  val Users_Info = new SequencedCollection[ServerUserInfo]("usersInfo", for {
    collection <- db.getOrCreateCollection[BsonDocument]("usersInfo")
    _ <- collection.createIndex(Indexes.ascending("userName", "_expireTime"), new IndexOptions().unique(true))
  } yield collection, Sequences)

  val Developer_VersionsInfo = new SequencedCollection[DeveloperVersionInfo]("developer.versionsInfo", for {
    collection <- db.getOrCreateCollection[BsonDocument]("developer.versionsInfo")
    _ <- collection.createIndex(Indexes.ascending("serviceName", "version", "_expireTime"), new IndexOptions().unique(true))
  } yield collection, Sequences)

  val Client_VersionsInfo = new SequencedCollection[ClientVersionInfo]("client.versionsInfo", for {
    collection <- db.getOrCreateCollection[BsonDocument]("client.versionsInfo")
    _ <- collection.createIndex(Indexes.ascending("serviceName", "version", "_expireTime"), new IndexOptions().unique(true))
  } yield collection, Sequences)

  val Developer_DesiredVersions = new SequencedCollection[DeveloperDesiredVersions]("developer.desiredVersions",
    db.getOrCreateCollection[BsonDocument]("developer.desiredVersions"), Sequences)

  val Client_DesiredVersions = new SequencedCollection[ClientDesiredVersions]("client.desiredVersions",
    db.getOrCreateCollection[BsonDocument]("client.desiredVersions"), Sequences)

  val Developer_DistributionClientsInfo = new SequencedCollection[DistributionClientInfo]("developer.distributionClientsInfo", for {
    collection <- db.getOrCreateCollection[BsonDocument]("developer.distributionClientsInfo")
    _ <- collection.createIndex(Indexes.ascending("distributionName", "_expireTime"), new IndexOptions().unique(true))
  } yield collection, Sequences)

  val Developer_DistributionClientsProfiles = new SequencedCollection[DistributionClientProfile]("developer.distributionClientsProfiles", for {
    collection <- db.getOrCreateCollection[BsonDocument]("developer.distributionClientsProfiles")
    _ <- collection.createIndex(Indexes.ascending("profileName", "_expireTime"), new IndexOptions().unique(true))
  } yield collection, Sequences)

  val State_InstalledDesiredVersions = new SequencedCollection[InstalledDesiredVersions]("state.installedDesiredVersions", for {
    collection <- db.getOrCreateCollection[BsonDocument]("state.installedDesiredVersions")
    _ <- collection.createIndex(Indexes.ascending("distributionName"))
  } yield collection, Sequences)

  val State_TestedVersions = new SequencedCollection[TestedDesiredVersions]("state.testedDesiredVersions", for {
    collection <- db.getOrCreateCollection[BsonDocument]("state.testedDesiredVersions")
    _ <- collection.createIndex(Indexes.ascending("profileName"))
  } yield collection, Sequences)

  val State_ServiceStates = new SequencedCollection[DistributionServiceState]("state.serviceStates", for {
    collection <- db.getOrCreateCollection[BsonDocument]("state.serviceStates")
    _ <- collection.createIndex(Indexes.ascending("sequence", "_expireTime"), new IndexOptions().unique(true))
    _ <- collection.createIndex(Indexes.ascending("distributionName"))
    _ <- collection.createIndex(Indexes.ascending("instance.instanceId"))
    _ <- collection.createIndex(Indexes.ascending("instance.service.date"), new IndexOptions().expireAfter(instanceStateExpireSec, TimeUnit.SECONDS))
    _ <- collection.dropItems()
  } yield collection, Sequences)

  val State_ServiceLogs = new SequencedCollection[ServiceLogLine]("state.serviceLogs", for {
    collection <- db.getOrCreateCollection[BsonDocument]("state.serviceLogs")
    _ <- collection.createIndex(Indexes.ascending("line.distributionName"))
  } yield collection, Sequences)

  val State_FaultReportsInfo = new SequencedCollection[DistributionFaultReport]("state.faultReportsInfo", for {
    collection <- db.getOrCreateCollection[BsonDocument]("state.faultReportsInfo")
    _ <- collection.createIndex(Indexes.ascending("distributionName"))
    _ <- collection.createIndex(Indexes.ascending("report.faultId"))
    _ <- collection.createIndex(Indexes.ascending("report.info.serviceName"))
  } yield collection, Sequences)

  val State_UploadStatus = for {
    collection <- db.getOrCreateCollection[UploadStatusDocument]("state.uploadStatus")
     _ <- collection.createIndex(Indexes.ascending("component"), new IndexOptions().unique(true))
  } yield collection

  def getNextSequence(sequenceName: String, increment: Int = 1): Future[Long] = {
    (for {
      sequences <- Sequences
      sequence <- { sequences.findOneAndUpdate(
        Filters.eq("name", sequenceName), Updates.inc("sequence", increment),
        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)) }
    } yield sequence).map(_.map(_.sequence).head)
  }

  def init()(implicit executionContext: ExecutionContext): Future[Unit] = {
    val filters = Filters.eq("content.userName", "admin")
    for {
      adminRecords <- Users_Info.find(filters)
    } yield {
      if (adminRecords.isEmpty) {
        Users_Info.insert(ServerUserInfo("admin", UserRole.Administrator.toString, PasswordHash("admin")))
      } else {
        Future()
      }
    }
  }
}