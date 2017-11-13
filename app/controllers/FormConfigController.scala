package controllers

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
    Ok("")
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

