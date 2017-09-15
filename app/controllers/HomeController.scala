package controllers

import play.Logger
import java.io.{BufferedInputStream, FileInputStream}
import java.nio.file.Files
import javax.inject._

import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc._
import scalikejdbc._
import com.ruimo.scoins.LoanPattern._
import com.ruimo.scoins.LoanPattern
import models.User

@Singleton
class HomeController @Inject()(
  cc: ControllerComponents,
  parsers: PlayBodyParsers
) extends AbstractController(cc) {
  implicit val session = AutoSession

  def index = Action {
    val users = sql"select * from users".toMap.list.apply()
println("users: " + users)

    Ok(views.html.index("Your new application is ready."))
  }

  def post = Action {
    println("HomeController.post")
    Ok("")
  }

  def skewCorrection = Action(parsers.multipartFormData) { req: Request[MultipartFormData[TemporaryFile]] =>
    req.body.file("credentials.json").map { crejson =>
      LoanPattern.using(Files.newInputStream(crejson.ref.path)) { is =>
        val json = Json.parse(is)
        val user = (json \ "user").as[String]
        val password = (json \ "password").as[String]
        if (User.isValidUser(user, password)) {
          Ok("")
        }
        else {
          BadRequest(
            Json.obj(
              "error" -> "InvalidUser"
            )
          )
        }
      }.recover {
        case t: Throwable =>
          Logger.error("Error in skeq correction.", t)
          throw t
      }.get
    }.getOrElse (
      BadRequest(
        Json.obj(
          "error" -> "NoCredentialFile"
        )
      )
    )
  }
}
