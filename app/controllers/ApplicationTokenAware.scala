package controllers

import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import models._
import java.sql.Connection

class ApplicationTokenInvalidException(config: JsValue) extends Exception("Application Token is invalid " + config)

trait ApplicationTokenAware {
  def isApplicationTokenValid(
    config: JsValue
  )(
    implicit applicationTokenRepo: ApplicationTokenRepo,
    conn: Connection
  ): Boolean = {
    val contractedUserId = (config \ "contractedUserId").as[String].toLong
    val apiKey = try {
      (config \ "apiKey").as[String].toLong
    } catch {
      case e: NumberFormatException =>
        throw new ApplicationTokenInvalidException(config)
      case t: Throwable => throw t
    }
    applicationTokenRepo.isValid(ContractedUserId(contractedUserId), apiKey)
  }
}
