package controllers

import java.time.format.DateTimeFormatter
import javax.inject._

import controllers.NeedLogin.Authenticated
import play.Logger
import models._
import play.api.data.Form
import play.api.data.validation.Constraints._
import play.api.data.Forms._
import play.api.mvc._
import helpers.Sanitize.{forUrl => sanitize}
import play.api.db.Database
import play.api.i18n.{Messages, MessagesProvider}

@Singleton
class ApplicationToken @Inject()(
  cc: MessagesControllerComponents,
  loginSessionRepo: LoginSessionRepo,
  authenticated: Authenticated,
  db: Database,
  applicationTokenRepo: ApplicationTokenRepo
) extends MessagesAbstractController(cc) {
  def dateTimeFormatter(implicit mp: MessagesProvider) = DateTimeFormatter.ofPattern(Messages("applicationTokenDateFormat"))

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    Ok(
      views.html.applicationToken(
        db.withConnection { implicit conn =>
          applicationTokenRepo.list(login.user.userId.get)
        },
        dateTimeFormatter
      )
    )
  }

  def create = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    try {
      db.withConnection { implicit conn =>
        applicationTokenRepo.create(login.user.userId.get)
      }
      Redirect(
        routes.ApplicationToken.index
      ).flashing(
        "message" -> Messages("created", Messages("applicationToken"))
      )
    }
    catch {
      case e: MaxApplicationTokenException =>
        Redirect(
          routes.ApplicationToken.index
        ).flashing(
          "errorMessage" -> Messages("tooManyToken", applicationTokenRepo.maxApplicationTokenCountByUser.toString)
        )

      case t: Throwable => throw t
    }
  }

  def remove(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    db.withConnection { implicit conn =>
      applicationTokenRepo.remove(ApplicationTokenId(id))
      Redirect(
        routes.ApplicationToken.index
      ).flashing(
        "message" -> Messages("removed", Messages("applicationToken"))
      )
    }
  }
}
