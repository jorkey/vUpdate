package com.vyulabs.update.distribution.graphql.distribution

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.stream.{ActorMaterializer, Materializer}
import com.vyulabs.update.common.config.{DistributionClientConfig, DistributionClientInfo}
import com.vyulabs.update.common.info.{DeveloperDesiredVersion, DeveloperDesiredVersions, UserInfo, UserRole}
import com.vyulabs.update.common.version.{DeveloperDistributionVersion, DeveloperVersion}
import com.vyulabs.update.distribution.TestEnvironment
import com.vyulabs.update.distribution.graphql.{GraphqlContext, GraphqlSchema}
import com.vyulabs.update.distribution.mongo.DistributionClientInfoDocument
import sangria.macros.LiteralGraphQLStringContext
import spray.json._

import scala.concurrent.ExecutionContext

class GetDesiredVersionsTest extends TestEnvironment {
  behavior of "Developer Desired Versions Client Requests"

  implicit val system = ActorSystem("Distribution")
  implicit val materializer: Materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(null, ex => { ex.printStackTrace(); log.error("Uncatched exception", ex) })

  override def dbName = super.dbName + "-distribution"

  override def beforeAll() = {
    val clientsInfoCollection = result(collections.Developer_DistributionClientsInfo)

    result(clientsInfoCollection.insert(DistributionClientInfoDocument(DistributionClientInfo("distribution1", DistributionClientConfig("common", None)))))

    result(collections.Developer_DesiredVersions.insert(DeveloperDesiredVersions(Seq(
      DeveloperDesiredVersion("service1", DeveloperDistributionVersion("test", DeveloperVersion(Seq(1)))),
      DeveloperDesiredVersion("service2", DeveloperDistributionVersion("test", DeveloperVersion(Seq(2))))))))
  }

  it should "get desired versions" in {
    val graphqlContext = new GraphqlContext(UserInfo("distribution1", UserRole.Distribution), workspace)

    assertResult((OK,
      ("""{"data":{"desiredVersions":[{"serviceName":"service1","version":"test-1"},{"serviceName":"service2","version":"test-2"}]}}""").parseJson))(
      result(graphql.executeQuery(GraphqlSchema.DistributionSchemaDefinition, graphqlContext, graphql"""
        query {
          desiredVersions {
             serviceName
             version
          }
        }
      """)))
  }
}
