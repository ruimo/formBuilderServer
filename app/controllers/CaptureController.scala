package controllers

import play.Logger
import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.file.Files
import javax.inject._

import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.mvc._
import scalikejdbc._
import com.ruimo.scoins.LoanPattern._
import com.ruimo.scoins.LoanPattern
import models.User
import akka.util.ByteString
import com.ruimo.scoins.Zip
import com.ruimo.graphics.twodim.Hugh
import com.ruimo.graphics.twodim.Hugh.FoundLine
import com.ruimo.graphics.twodim.Degree.toRadian
import scala.math.Pi

import scala.collection.{immutable => imm}
import com.ruimo.graphics.twodim.Range

import scala.collection.immutable.IndexedSeq

@Singleton
class CaptureController @Inject()(
  cc: ControllerComponents,
  parsers: PlayBodyParsers
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

  def index = Action(parse.temporaryFile) { req: Request[TemporaryFile] =>
    val dir = Files.createTempDirectory(null);
    Zip.explode(req.body.path, dir)
    
    println("dir: " + dir)
    val config: JsValue = Json.parse(Files.readAllBytes(dir.resolve("config.json")))
    println("config: " + config)
    val inputFiles = (config \ "inputFiles").as[Seq[String]]
    val skewCorrection = (config \ "skewCorrection")
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
        dir.resolve(inputFiles(0)),
        3000, 200, thRange = range
      )
      val lineCount = (skewCorrection \ "lineCount").as[Int]
      val taken = found.take(lineCount)
      println("found: " + taken)
      val ave = average(taken)
      println("average: " + ave)
      val resp = Json.obj(
        "skewCorrection" -> Json.obj(
          "foundLines" -> JsArray(
            taken.map { line =>
              Json.obj(
                "ro" -> line.ro,
                "th" -> line.th
              )
            }
          )
        )
      )
      println("resp = " + resp)
Ok(resp)
      // Ok(
      //   Json.obj(
      //     "skewCorrection" -> Json.obj(
      //       "foundLines" -> JsArray(
      //         taken.map { line =>
      //           Json.obj(
      //             "ro" -> line.ro,
      //             "th" -> line.th
      //           )
      //         }
      //       )
      //     )
      //   )
      // )
    } else {
      Ok("")
    }
  }
}
