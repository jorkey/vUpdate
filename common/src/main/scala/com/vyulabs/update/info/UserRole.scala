package com.vyulabs.update.info

import spray.json.{JsString, JsValue, RootJsonFormat}

object UserRole extends Enumeration {
  type UserRole = Value
  val None, Administrator, Distribution, Service = Value

  implicit object UserRoleJsonFormat extends RootJsonFormat[UserRole] {
    def write(value: UserRole) = JsString(value.toString)
    def read(value: JsValue) = withName(value.asInstanceOf[JsString].value)
  }
}