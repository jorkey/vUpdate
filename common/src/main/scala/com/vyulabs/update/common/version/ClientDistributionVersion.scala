package com.vyulabs.update.common.version

import com.vyulabs.update.common.common.Common.DistributionName
import spray.json.DefaultJsonProtocol._


/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 26.04.19.
  * Copyright FanDate, Inc.
  */

case class ClientDistributionVersion(distributionName: DistributionName, build: ClientVersion) {
  def original() = DistributionVersion(distributionName, build.build)

  def next() = ClientDistributionVersion(distributionName, build.next())

  override def toString: String = {
    distributionName + "-" + build.toString
  }
}

object ClientDistributionVersion {
  implicit val clientDistributionVersionJson = jsonFormat2(ClientDistributionVersion.apply)

  def from(version: DistributionVersion): ClientDistributionVersion =
    ClientDistributionVersion(version.distributionName, ClientVersion(version.build, 0))

  def parse(version: String): ClientDistributionVersion = {
    val index = version.lastIndexOf('-')
    if (index == -1) {
      throw new IllegalArgumentException(s"Invalid version ${version}")
    }
    val distributionName = version.substring(0, index)
    val body = if (index != -1) version.substring(index + 1) else version
    new ClientDistributionVersion(distributionName, ClientVersion.parse(body))
  }

  val ordering: Ordering[ClientDistributionVersion] = Ordering.fromLessThan[ClientDistributionVersion]((version1, version2) => {
    if (version1.distributionName != version2.distributionName) {
      version1.distributionName.compareTo(version2.distributionName) < 0
    } else {
      ClientVersion.ordering.lt(version1.build, version2.build)
    }
  })
}


