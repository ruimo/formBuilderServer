package controllers

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
import scalikejdbc._
import com.ruimo.scoins.LoanPattern._
import models.User
import akka.util.ByteString
import com.ruimo.graphics.twodim.Hugh
import com.ruimo.graphics.twodim.Hugh.FoundLine
import com.ruimo.graphics.twodim.Degree.toRadian

import scala.math.Pi
import scala.collection.{immutable => imm}
import com.ruimo.graphics.twodim.Range

import scala.collection.immutable.IndexedSeq

case class SkewCorrectionResult(
  foundLines: imm.Seq[FoundLine]
)

@Singleton
class PrepareController @Inject()(
  cc: ControllerComponents,
  parsers: PlayBodyParsers,
  tempFileCreator: TemporaryFileCreator
) extends AbstractController(cc) {
  implicit val session = AutoSession

  def average(lines: IndexedSeq[FoundLine]): Double = {
    val (totalCount: Long, thSum: Double) = lines.foldLeft((0L, 0.0)) { (sum, e) =>
      (
        sum._1 + e.count,
        sum._2 + e.ro.signum * e.th * e.count
      )
    }
    thSum / totalCount
  }

  def index = Action(parse.byteString) { req: Request[ByteString] =>
    req.body.foreach { b =>
      println(b.toHexString)
    }
    println("End capture")
    Ok("")
  }

  private[controllers] def performSkewCorrection(
    inputFile: Path, skewCorrection: JsLookupResult
  ): Option[SkewCorrectionResult] =
    if ((skewCorrection \ "enabled").as[Boolean]) {
      val maxAngleToDetect = (skewCorrection \ "maxAngleToDetect").as[Double]
      val range: imm.Seq[Range] =
        if ((skewCorrection \ "direction").as[String] == "vertical") {
          imm.Seq(
            Range(min = toRadian(-maxAngleToDetect), max = toRadian(maxAngleToDetect)),
            Range(min = toRadian(180 - maxAngleToDetect), max = Pi)
          )
        }
        else {
          imm.Seq(
            Range(min = toRadian(90 - maxAngleToDetect / 2), max = toRadian(90 + maxAngleToDetect / 2))
          )
        }

      val found: imm.IndexedSeq[FoundLine] = Hugh.perform(
        inputFile, 3000, 200, thRange = range
      )
      val lineCount = (skewCorrection \ "lineCount").as[Int]
      val taken = found.take(lineCount)
      println("found: " + taken)
      val ave = average(taken)
      println("average: " + ave)

      if (ave.abs < 1) {
        // Horizontal skew correction
        Logger.info("Horizontal skew correction angle: " + -ave)
        RotateImage.perform(inputFile, -ave)
      }
      else {
        // Vertical skew correction
        val verticalAngle = if (ave < 0) ave + Pi / 2 else ave - Pi / 2
        Logger.info("Horizontal skew correction angle: " + -verticalAngle)
        RotateImage.perform(inputFile, -verticalAngle)
      }
      Some(
        SkewCorrectionResult(
          foundLines = taken
        )
      )
    }
    else None

  private[controllers] def toArea(ar: Array[Double]) = Area(
    Percent(ar(0)), Percent(ar(1)), Percent(ar(2)), Percent(ar(3))
  )

  private[controllers] def performCrop(inputFile: Path, crop: JsLookupResult) {
    if ((crop \ "enabled").as[Boolean]) {
      val top = (crop \ "top").as[Array[Double]]
      val bottom = (crop \ "bottom").as[Array[Double]]
      val left = (crop \ "left").as[Array[Double]]
      val right = (crop \ "right").as[Array[Double]]

      val cropped = Crop.edgeCrop(
        toArea(left), toArea(top), toArea(right), toArea(bottom),
        ImageIO.read(inputFile.toFile), BlackEdgeSearchStrategy(253)
      )
      ImageIO.write(cropped, "png", inputFile.toFile)
    }
  }

  def perform = Action(parse.temporaryFile) { req: Request[TemporaryFile] =>
    PathUtil.withTempDir(None) { dir =>
      Zip.explode(req.body.path, dir)
      println("dir: " + dir)

      val config: JsValue = Json.parse(Files.readAllBytes(dir.resolve("config.json")))
      println("config: " + config)
      val inputFiles = (config \ "inputFiles").as[Seq[String]]
      val inFile = dir.resolve(inputFiles(0))
      val skewCorrection = (config \ "skewCorrection")
      val crop = (config \ "crop")

      val skewResult: Option[SkewCorrectionResult] = performSkewCorrection(inFile, skewCorrection)
      val skewCorrectedFile = Files.createTempFile(dir, null, null)
      Files.copy(inFile, skewCorrectedFile, StandardCopyOption.REPLACE_EXISTING)
      val cropFailed: Boolean = try {
        performCrop(inFile, crop)
        false
      }
      catch {
        case e: IllegalStateException =>
          e.printStackTrace()
          true
        case t: Throwable => throw t
      }

      val fileToServe: Path = Files.createTempFile(null, null)
      val resp = new JsObject(
        skewResult.map { r =>
          Map(
            "skewCorrection" -> Json.obj(
              "foundLines" -> JsArray(
                r.foundLines.map { line =>
                  Json.obj(
                    "ro" -> line.ro,
                    "th" -> line.th
                  )
                }
              ),
              "correctedFiles" -> JsArray(Seq(JsString(skewCorrectedFile.getFileName.toString)))
            )
          )
        }.getOrElse(Map()) ++ (
          if ((crop \ "enabled").as[Boolean]) Map(
            "crop" -> (
              if (cropFailed) Json.obj(
                "status" -> "CannotFindEdge"
              )
              else Json.obj(
                "status" -> "Success",
                "correctedFiles" -> JsArray(Seq(JsString(inFile.getFileName.toString)))
              )
            )
          )
          else Map()
        )
      )
      println("resp: " + resp)
      LoanPattern.using(Files.createTempFile(null, null)) { respFile =>
        Files.write(respFile, resp.toString.getBytes("utf-8"))

        Zip.deflate(
          fileToServe,
          Seq(
            "response.json" -> respFile,
            skewCorrectedFile.getFileName.toString -> skewCorrectedFile,
            inFile.getFileName.toString -> inFile
          )
        )
      } (f => Files.delete(f)).get

      Ok.sendPath(fileToServe, onClose = () => Files.delete(fileToServe))
    }.get
  }
}
