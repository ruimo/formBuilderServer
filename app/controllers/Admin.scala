package controllers

import javax.inject._

import controllers.NeedLogin.Authenticated
import play.Logger
import models.{LoginSessionRepo, LoginUser, User, LoginSession}
import play.api.data.Form
import play.api.data.validation.Constraints._
import play.api.data.Forms._
import play.api.mvc._
import helpers.Sanitize.{forUrl => sanitize}
import play.api.db.Database
import play.api.i18n.Messages

@Singleton
class Admin @Inject()(
  cc: MessagesControllerComponents,
  parsers: PlayBodyParsers,
  loginSessionRepo: LoginSessionRepo,
  authenticated: Authenticated,
  db: Database
) extends MessagesAbstractController(cc) {
  val loginForm = Form(
    mapping(
      "userName" -> text.verifying(nonEmpty),
      "password" -> text.verifying(nonEmpty),
      "uri" -> text
    )(LoginUser.apply)(LoginUser.unapply)
  )

  def index() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    NeedLogin.assumeAdmin(login) {
      Ok(views.html.admin.index())
    }
  }

  def startFirstSetup() = Action { implicit request: MessagesRequest[AnyContent] =>
    Redirect(
      routes.Admin.startLogin(routes.Admin.index().url)
    ).flashing(
      "message" -> Messages("firstLogin")
    )
  }

  def startLogin(uriOnLoginSuccess: String) = Action { implicit request: MessagesRequest[AnyContent] =>
    if (loginSessionRepo.fromRequest(request).isDefined)
      Redirect(uriOnLoginSuccess)
    else
      Ok(
        views.html.admin.login(
          loginForm, sanitize(uriOnLoginSuccess)
        )
      )
  }

  def onValidationErrorInLogin(form: Form[LoginUser])(implicit request: MessagesRequest[AnyContent]) = {
    Logger.error("Validation error in NeedLogin.login.")
    BadRequest(
      views.html.admin.login(
        form, form("uri").value.get
      )
    )
  }

  def login = Action { implicit request: MessagesRequest[AnyContent] =>
    val form = loginForm.bindFromRequest
    form.fold(
      onValidationErrorInLogin,
      user => tryLogin(user, form)
    )
  }

  def tryLogin(
    user: LoginUser, form: Form[LoginUser]
  )(implicit request: MessagesRequest[AnyContent]): Result = db.withConnection { implicit conn =>
    User.login(user.userName, user.password) match {
      case Some(rec) =>
        Logger.info("Password ok '" + user.userName + "'")
        val resp = Redirect(user.uri).withSession {
          (loginSessionRepo.loginUserKey,
            loginSessionRepo.serialize(rec.userId.get.value, System.currentTimeMillis + loginSessionRepo.sessionTimeout))
        }
        val msg = Messages("welcome")
        if (!msg.isEmpty) resp.flashing("message" -> msg) else resp
        resp
      case None =>
        Logger.error("User name or password invali '" + user.userName + "'")
        BadRequest(
          views.html.admin.login(
            form.withGlobalError(Messages("cannotLogin")),
            form("uri").value.get
          )
        )
    }
  }

  def logoff(uriOnLogoffSuccess: String) = Action { implicit request: MessagesRequest[AnyContent] =>
    Redirect(routes.HomeController.index).withSession(request.session - loginSessionRepo.loginUserKey)
  }
}

