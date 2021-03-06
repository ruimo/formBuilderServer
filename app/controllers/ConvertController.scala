package controllers

import scala.sys.process.Process
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json._
import java.nio.file.{Files, Path, StandardCopyOption}

import play.Logger
import java.io.{BufferedInputStream, File, FileInputStream}
import javax.inject._
import javax.imageio.ImageIO

import com.ruimo.graphics.adapter.{Ocr, OcrResult, RotateImage}
import com.ruimo.graphics.twodim.{Area, BlackEdgeSearchStrategy, Crop}
import com.ruimo.scoins.{LoanPattern, PathUtil, Percent, Zip}
import play.api.libs.Files.TemporaryFile
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.mvc._
import com.ruimo.scoins.LoanPattern._
import models.{ApplicationTokenRepo, User}
import akka.util.ByteString
import com.ruimo.graphics.twodim.Hugh
import com.ruimo.graphics.twodim.Hugh.FoundLine
import com.ruimo.graphics.twodim.Degree.toRadian

import scala.math.Pi
import scala.collection.{immutable => imm}
import com.ruimo.graphics.twodim.Range
import play.api.db.Database

import scala.collection.immutable.IndexedSeq

// This is non-standard API and will change without any notice.
@Singleton
class ConvertController @Inject()(
  cc: ControllerComponents,
  parsers: PlayBodyParsers,
  tempFileCreator: TemporaryFileCreator,
  db: Database,
  implicit val applicationTokenRepo: ApplicationTokenRepo
) extends AbstractController(cc) with ApplicationTokenAware {
  private[this] def pdfToPng(file: Path, workDir: Path, outPrefix: String): Int = {
    import scala.language.postfixOps

    Files.createDirectories(workDir.resolve(outPrefix).getParent())
    val cmd = "pdfimages -png " + file.toAbsolutePath + " " + outPrefix
    val rc = (
      Process(cmd, Some(workDir.toFile)) run
    ) exitValue()

    if (rc == 0) {
      Logger.info("'" + cmd + "' success.")
    }
    else {
      Logger.error("'" + cmd + "' failed. rc = " + rc)
    }
    Files.delete(file)
    rc
  }

  def tifToPng(file: Path, workDir: Path): (Int, Path) = {
    val fileName = file.getFileName.toString
    val pngFile: Path = file.resolveSibling(fileName + ".png")
    val rc = tifToPng(file, pngFile, workDir)

    if (rc == 0) {
      Files.delete(file)
    }
    (rc, pngFile)
  }

  private[this] def tifToPng(file: Path, toFile: Path, workDir: Path): Int = {
    import scala.language.postfixOps

    val fileName = file.getFileName.toString
    Files.createDirectories(toFile.getParent())
    val cmd = "convert " + file.toAbsolutePath + " " + toFile.toAbsolutePath
    Logger.info("Convert to png: '" + cmd + "'")
    val rc = (
      Process(cmd, Some(workDir.toFile)) run
    ) exitValue()

    if (rc == 0) {
      Logger.info("'" + cmd + "' success.")
    }
    else {
      Logger.error("'" + cmd + "' failed. rc = " + rc)
    }
    rc
  }

  def perform = Action(parse.temporaryFile) { req: Request[TemporaryFile] =>
    Logger.info("ConvertController.perform() called.")
    PathUtil.withTempDir(None) { dir =>
      val file: Path = req.body.path
      Zip.explode(req.body.path, dir)
      val config: JsValue = Json.parse(Files.readAllBytes(dir.resolve("config.json")))
      println("config: " + config)

      try {
        val isAuthenticated: Boolean = db.withConnection { implicit conn =>
          isApplicationTokenValid((config \ "auth").as[JsObject])
        }
        println("isAuthenticated: " + isAuthenticated)

        if (isAuthenticated) {
          val inputFile = (config \ "inputFile").as[String]
          val inFile = dir.resolve(inputFile)
          val fileName: String = inFile.getFileName.toString.toLowerCase

          if (fileName.endsWith(".pdf")) {
            PathUtil.withTempDir(None) { out =>
              pdfToPng(inFile, out, fileName) match {
                case 0 =>
                  val fileToServe = Files.createTempFile(null, ".zip")
                  Zip.deflate(
                    fileToServe,
                    Files.list(out).toArray(size => new Array[Path](size)).toSeq.map { e =>
                      e.getFileName.toString -> e
                    }
                  )
                  Ok.sendPath(fileToServe, onClose = () => Files.delete(fileToServe))
                case rc =>
                  Logger.error("Cannot convert pdf to png (rc = " + rc + ")")
                  BadRequest("Cannot convert pdf to png (rc = " + rc + ")")
              }
            }.get
          }
          else if (fileName.endsWith(".tif") || fileName.endsWith(".tiff")) {
            PathUtil.withTempDir(None) { out =>
              val (rc: Int, pngFile: Path) = tifToPng(inFile, out)
              rc match {
                case 0 =>
                  val fileToServe = Files.createTempFile(null, ".zip")
                  Zip.deflate(
                    fileToServe,
                    Seq(
                      pngFile.getFileName.toString -> pngFile
                    )
                  )
                  Ok.sendPath(fileToServe, onClose = () => Files.delete(fileToServe))
                case rc =>
                  Logger.error("Cannot convert pdf to png (rc = " + rc + ")")
                  BadRequest("Cannot convert tif to png (rc = " + rc + ")")
              }
            }.get
          }
          else {
            Logger.error("Invalid file type. PDF/TIF are supported '" + fileName + "'")
            BadRequest("Invalid file type. PDF/TIF are supported '" + fileName + "'")
          }
        }
        else {
          Forbidden("Application token does not match.")
        }
      } catch {
        case e: ApplicationTokenInvalidException =>
          Logger.error("Invalid application token.", e)
          Forbidden(e.getMessage)
        case t: Throwable =>
          Logger.error("Unknown error.", t)
          throw t
      }
    }.get
  }
}
