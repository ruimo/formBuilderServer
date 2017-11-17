package controllers

import java.nio.file.Path
import javax.inject.{Inject, Singleton}

import models.ApplicationTokenRepo
import play.api.db.Database
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{AbstractController, MessagesControllerComponents, PlayBodyParsers, Request}
import play.Logger
import com.ruimo.scoins.{PathUtil, Zip}
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import java.nio.file.Files

// This is standard API
@Singleton
class FormConfigController @Inject()(
  cc: MessagesControllerComponents,
  parsers: PlayBodyParsers,
  db: Database,
  implicit val applicationTokenRepo: ApplicationTokenRepo
) extends AbstractController(cc) with ApplicationTokenAware {
  def list = Action(parse.temporaryFile) { req: Request[TemporaryFile] =>
    Logger.info("FormConfigController.list() called.")
    PathUtil.withTempDir(None) { dir =>
      val file: Path = req.body.path
      Zip.explode(req.body.path, dir)
      val config: JsValue = Json.parse(Files.readAllBytes(dir.resolve("config.json")))
      println("config: " + config)

      val isAuthenticated: Boolean = db.withConnection { implicit conn =>
        isApplicationTokenValid((config \ "auth").as[JsObject])
      }
      println("isAuthenticated: " + isAuthenticated)

      if (isAuthenticated) {
        db.withConnection { implicit conn =>
          Ok(
            Json.obj(
            )
          )
        }
      }
      else {
        Forbidden("Application token does not match.")
      }
    }.get
  }

  def save = Action(parse.temporaryFile) { req: Request[TemporaryFile] =>
    Logger.info("FormConfigController.save() called.")
    PathUtil.withTempDir(None) { dir =>
      Zip.explode(req.body.path, dir)
      val config: JsValue = Json.parse(Files.readAllBytes(dir.resolve("config.json")))

      Ok("")
    }.get
  }

  def load = Action(parse.temporaryFile) { req: Request[TemporaryFile] =>
    Ok("")
  }

  def remove = Action(parse.temporaryFile) { req: Request[TemporaryFile] =>
    Ok("")
  }
}

