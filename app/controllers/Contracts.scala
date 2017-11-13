package controllers

import java.time.format.DateTimeFormatter
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
import play.api.i18n.{Messages, MessagesProvider}

@Singleton
class Contracts @Inject()(
  cc: ControllerComponents,
  parsers: PlayBodyParsers,
  authenticated: NeedLogin.Authenticated,
  loginSessionRepo: LoginSessionRepo,
  contractedUserRepo: ContractedUserRepo,
  db: Database
) extends AbstractController(cc) {
  def dateTimeFormatter(implicit mp: MessagesProvider) = DateTimeFormatter.ofPattern(Messages("contractDateFormat"))

  def index(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    Ok(
      views.html.contracts(
        db.withConnection { implicit conn =>
          contractedUserRepo.list(
            page, pageSize, OrderBy(orderBySpec),
            byUser = login.user.userId, byPlan = None
          )
        },
        dateTimeFormatter
      )
    )
  }
}
