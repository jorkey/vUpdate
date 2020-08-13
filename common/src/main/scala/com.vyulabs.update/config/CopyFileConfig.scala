package com.vyulabs.update.config

import spray.json.DefaultJsonProtocol

case class CopyFileConfig(sourceFile: String, destinationFile: String, except: Set[String], settings: Map[String, String])

object CopyFileConfig extends DefaultJsonProtocol {
  implicit val copyFileConfigJson = jsonFormat4(CopyFileConfig.apply)
}

