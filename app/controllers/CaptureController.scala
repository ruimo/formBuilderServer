package controllers

import play.Logger
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.inject._

import com.ruimo.graphics.adapter.Ocr
import com.ruimo.graphics.twodim.{Area, Crop}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.mvc._
import com.ruimo.scoins.{PathUtil, Zip}
import models.ApplicationTokenRepo

import scala.collection.{immutable => imm}
import play.api.db.Database

// This is non-standard API and will change without any notice.
@Singleton
class CaptureController @Inject()(
  cc: ControllerComponents,
  parsers: PlayBodyParsers,
  prepareController: PrepareController,
  db: Database,
  implicit val applicationTokenRepo: ApplicationTokenRepo
) extends AbstractController(cc) with ApplicationTokenAware {
  def perform = Action(parse.temporaryFile) { req: Request[TemporaryFile] =>
    PathUtil.withTempDir(None) { dir =>
      Zip.explode(req.body.path, dir)
      println("dir: " + dir)

      val config: JsValue = Json.parse(Files.readAllBytes(dir.resolve("config.json")))
      println("config: " + config)

      val isAuthenticated: Boolean = db.withConnection { implicit conn =>
        isApplicationTokenValid((config \ "auth").as[JsObject])
      }

      println("isAuthenticated: " + isAuthenticated)
      if (isAuthenticated) {
        val inputFiles = (config \ "inputFiles").as[Seq[String]]
        val inFile = dir.resolve(inputFiles(0))
        val skewCorrection = (config \ "skewCorrection")
        val crop = (config \ "crop")

        val skewResult: Option[SkewCorrectionResult] = prepareController.performSkewCorrection(inFile, skewCorrection)
        prepareController.performCrop(inFile, crop)

        val absoluteFields = (config \ "absoluteFields").as[Seq[JsValue]]
        val image = ImageIO.read(inFile.toFile)

        var fieldImageSeq = 0
        val capturedFields = JsArray(
          absoluteFields.map { e =>
            val fieldName: String = (e \ "name").as[String]
            val rect: Area = prepareController.toArea((e \ "rect").as[Array[Double]])

            val fieldImage = Crop.simpleCrop(
              rect.toPixelArea(image.getWidth(), image.getHeight()),
              image
            )
            val fieldFile = Files.createTempFile(dir, "%08d".format(fieldImageSeq), ".png")
            fieldImageSeq += 1
            ImageIO.write(fieldImage, "png", fieldFile.toFile)

            val ocrResult = Ocr.perform(fieldImage, option = "digits")
            println("ocrResult: " + ocrResult)

            Json.obj(
              "fieldName" -> fieldName,
              "base64Image" -> ocrResult.base64Img,
            "text" -> ocrResult.text,
              "rawText" -> ocrResult.rawText
            )
          }
        )

        Ok(
          Json.obj(
            "capturedFields" -> capturedFields
          )
        )
      }
      else {
        Forbidden("Application token does not match.")
      }
    }.get
  }
}
