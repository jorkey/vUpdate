package com.vyulabs.update.common.distribution.server

import com.vyulabs.update.common.common.Common
import com.vyulabs.update.common.common.Common.{DistributionName, ServiceName}
import com.vyulabs.update.common.utils.IoUtils
import com.vyulabs.update.common.version.{ClientDistributionVersion, ClientVersion, DistributionVersion, Version}
import org.slf4j.LoggerFactory

import java.io._

/**
  * Created by Andrei Kaplanov (akaplanov@vyulabs.com) on 23.04.19.
  * Copyright FanDate, Inc.
  */
class DistributionDirectory(val directory: File) {
  private implicit val log = LoggerFactory.getLogger(this.getClass)

  private val directoryDir = new File(directory, "directory")
  private val developerDir = new File(directoryDir, "developer")
  private val developerServicesDir = new File(developerDir, "services")
  private val clientDir = new File(directoryDir, "client")
  private val clientServicesDir = new File(clientDir, "services")
  private val faultsDir = new File(directoryDir, "faults")

  private val builderDir = new File(directory, "builder")

  if (!directory.exists()) directory.mkdirs()

  if (!directoryDir.exists()) directoryDir.mkdir()
  if (!developerDir.exists()) developerDir.mkdir()
  if (!developerServicesDir.exists()) developerServicesDir.mkdir()
  if (!clientDir.exists()) clientDir.mkdir()
  if (!clientServicesDir.exists()) clientServicesDir.mkdir()
  if (!faultsDir.exists()) faultsDir.mkdir()

  if (!builderDir.exists()) builderDir.mkdir()

  def getConfigFile(): File = {
    new File(directory, Common.DistributionConfigFileName)
  }

  def getDeveloperVersionImageFileName(serviceName: ServiceName, version: Version): String = {
    serviceName + "-" + version + ".zip"
  }

  def getClientVersionImageFileName(serviceName: ServiceName, version: ClientVersion): String = {
    serviceName + "-" + version + ".zip"
  }

  def getFaultReportFileName(faultId: String): String = {
    faultId + "-fault.zip"
  }

  def drop(): Unit = {
    IoUtils.deleteFileRecursively(directory)
  }

  def getDeveloperServiceDir(distributionName: DistributionName, serviceName: ServiceName): File = {
    val dir1 = new File(developerServicesDir, distributionName)
    if (!dir1.exists()) dir1.mkdir()
    val dir2 = new File(dir1, serviceName)
    if (!dir2.exists()) dir2.mkdir()
    dir2
  }

  def getClientServiceDir(distributionName: DistributionName, serviceName: ServiceName): File = {
    val dir1 = new File(clientServicesDir, distributionName)
    if (!dir1.exists()) dir1.mkdir()
    val dir2 = new File(dir1, serviceName)
    if (!dir2.exists()) dir2.mkdir()
    dir2
  }

  def getDeveloperVersionImageFile(serviceName: ServiceName, version: DistributionVersion): File = {
    new File(getDeveloperServiceDir(version.distributionName, serviceName), getDeveloperVersionImageFileName(serviceName, version.build))
  }

  def getClientVersionImageFile(serviceName: ServiceName, version: ClientDistributionVersion): File = {
    new File(getClientServiceDir(version.distributionName, serviceName), getClientVersionImageFileName(serviceName, version.build))
  }

  def getFaultsDir(): File = {
    faultsDir
  }

  def getFaultReportFile(faultId: String): File = {
    new File(faultsDir, getFaultReportFileName(faultId))
  }

  def getBuilderDir(): File = {
    builderDir
  }
}
