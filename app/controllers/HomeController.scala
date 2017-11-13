package controllers

import play.Logger
import java.io.{BufferedInputStream, FileInputStream}
import java.nio.file.Files
import javax.inject._

import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc._
import com.ruimo.scoins.LoanPattern._
import com.ruimo.scoins.LoanPattern
import controllers.NeedLogin.OptAuthenticated
import models.{LoginSessionRepo, User, ContractedUserRepo, LoginSession, PagedRecords, OrderBy}
import play.api.db.Database

@Singleton
class HomeController @Inject()(
  cc: ControllerComponents,
  parsers: PlayBodyParsers,
  optAuthenticated: OptAuthenticated,
  loginSessionRepo: LoginSessionRepo,
  contractedUserRepo: ContractedUserRepo,
  db: Database
) extends AbstractController(cc) {
  def index = optAuthenticated { implicit request: MessagesRequest[AnyContent] =>
    implicit val optLogin: Option[LoginSession] = loginSessionRepo.fromRequest(request)
    Ok(views.html.index())
  }
}
