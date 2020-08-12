package com.vyulabs.update.distribution

import com.vyulabs.update.common.Common.ServiceName
import com.vyulabs.update.version.BuildVersion

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 23.04.19.
  * Copyright FanDate, Inc.
  */
trait DistributionWebPaths {
  // New paths

  val apiPathPrefix = "api"
  val uiPathPrefix = "ui"

  // New API paths

  val userInfoPath = "user-info"
  val clientsInfoPath = "clients-info"
  val instanceVersionsPath = "instance-versions"
  val versionImagePath = "version-image"
  val versionInfoPath = "version-info"
  val versionsInfoPath = "versions-info"
  val desiredVersionsPath = "desired-versions"
  val desiredVersionPath = "desired-version"
  val serviceFaultPath = "service-fault"
  val distributionVersionPath = "distribution-version"
  val scriptsVersionPath = "scripts-version"

  // Deprecated API paths

  val downloadVersionPath = "download-version"
  val downloadVersionInfoPath = "download-version-info"
  val downloadVersionsInfoPath = "download-versions-info"
  val downloadDesiredVersionsPath = "download-desired-versions"
  val downloadDesiredVersionPath = "download-desired-version"
  val getDistributionVersionPath = "get-distribution-version"
  val getScriptsVersionPath = "get-scripts-version"

  val uploadVersionPath = "upload-version"
  val uploadVersionInfoPath = "upload-version-info"
  val uploadDesiredVersionsPath = "upload-desired-versions"
  val uploadServiceFaultPath = "upload-service-fault"

  // Old API

  val browsePath = "browse"
  val pingPath = "ping"

  // Names

  val versionName = "version"
  val versionInfoName = "version-info"
  val desiredVersionsName = "desired-versions"

  def getDownloadVersionPath(serviceName: ServiceName, version: BuildVersion): String = {
    downloadVersionPath + "/" + serviceName + "/" + version.toString
  }

  def getDownloadVersionInfoPath(serviceName: ServiceName, version: BuildVersion): String = {
    downloadVersionInfoPath + "/" + serviceName + "/" + version.toString
  }

  def getUploadVersionPath(serviceName: ServiceName, version: BuildVersion): String = {
    uploadVersionPath + "/" + serviceName + "/" + version.toString
  }

  def getUploadVersionInfoPath(serviceName: ServiceName, version: BuildVersion): String = {
    uploadVersionInfoPath + "/" + serviceName + "/" + version.toString
  }

  def getServiceFaultPath(serviceName: ServiceName): String = {
    serviceFaultPath + "/" + serviceName
  }
}
