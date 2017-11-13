package controllers

import java.sql.Connection
import javax.inject.Inject

import helpers.PasswordHash
import models.{LoginSession, LoginSessionRepo, User}
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc.Security.{AuthenticatedBuilder, AuthenticatedRequest}
import play.api.mvc._
import play.api.mvc.Results.{Redirect, Unauthorized}

import scala.concurrent.{ExecutionContext, Future}
import play.api.Configuration
import play.api.db.Database
import play.api.libs.json.Json

object NeedLogin {
  private[this] def createFirstUser()(implicit conn: Connection) {
    val password: String = PasswordHash.password()
    val firstUser = User.createFirstUser(password)
    Logger.info(s"""*** Login with the following Information ***
User: ${firstUser.userName}
Password: ${password}
""")
  }

  def onUnauthorized(request: RequestHeader)(implicit conn: Connection): Result =
    User.count match {
      case 0 =>
        Logger.info("User table empty. Go to first setup page.")
        createFirstUser()
        Redirect(routes.Admin.startFirstSetup())
      case _ =>
        Logger.info("User table is not empty. Go to login page.")
        Redirect(
          if (request.method.equalsIgnoreCase("get"))
            routes.Admin.startLogin(request.uri)
          else
            routes.HomeController.index
        )
    }

  def onUnauthorizedJson(request: RequestHeader)(implicit conn: Connection): Result =
    User.count match {
      case 0 =>
        Logger.info("User table empty. Go to first setup page.")
        createFirstUser()
        Unauthorized(Json.toJson(Map("status" -> "Redirect", "url" -> routes.Admin.startFirstSetup().url)))

      case _ =>
        Logger.info("User table is not empty. Go to login page.")
        val urlAfterLogin: String =
          request.getQueryString("urlAfterLogin").getOrElse(routes.HomeController.index.url)
            .replace("&amp;", "&") // Dirty hack

        Unauthorized(
          Json.toJson(
            Map(
              "status" -> "Redirect",
              "url" -> routes.Admin.startLogin(urlAfterLogin).url
            )
          )
        )
    }

  class UserAuthenticatedBuilder (
    parser: BodyParser[AnyContent],
    loginSessionRepo: LoginSessionRepo
  )(
    implicit ec: ExecutionContext,
    db: Database
  ) extends AuthenticatedBuilder[LoginSession](
    { req: RequestHeader =>
      loginSessionRepo.fromRequest(req)
    },
    parser,
    req => db.withConnection { implicit conn => onUnauthorized(req) }
  ) {
    @Inject()
    def this (
      parser: BodyParsers.Default,
      loginSessionRepo: LoginSessionRepo
    )(
      implicit ec: ExecutionContext,
      db: Database
    ) = {
      this (parser: BodyParser[AnyContent], loginSessionRepo)
    }
  }

  class UserAuthenticatedBuilderJson (
    parser: BodyParser[AnyContent],
    loginSessionRepo: LoginSessionRepo
  )(
    implicit ec: ExecutionContext,
    db: Database
  ) extends AuthenticatedBuilder[LoginSession](
    { req: RequestHeader =>
      loginSessionRepo.fromRequest(req)
    },
    parser,
    req => db.withConnection { implicit conn => onUnauthorizedJson(req) }
  ) {
    @Inject()
    def this (
      parser: BodyParsers.Default,
      loginSessionRepo: LoginSessionRepo
    )(
      implicit ec: ExecutionContext,
      db: Database
    ) = {
      this (parser: BodyParser[AnyContent], loginSessionRepo)
    }
  }

  class Authenticated(
    val parser: BodyParser[AnyContent],
    messagesApi: MessagesApi,
    builder: AuthenticatedBuilder[LoginSession]
  )(
    implicit val executionContext: ExecutionContext
  ) extends ActionBuilder[AuthMessagesRequest, AnyContent] {
    type ResultBlock[A] = (AuthMessagesRequest[A]) => Future[Result]

    @Inject
    def this (
      parser: BodyParsers.Default,
      messagesApi: MessagesApi,
      builder: UserAuthenticatedBuilder
    )(implicit ec: ExecutionContext) =
      this (parser: BodyParser[AnyContent], messagesApi, builder)

    def invokeBlock[A](request: Request[A], block: ResultBlock[A]): Future[Result] =
      builder.authenticate(request, { authRequest: AuthenticatedRequest[A, LoginSession] =>
        block(new AuthMessagesRequest[A](authRequest.user, messagesApi, request))
      })
  }

  class AuthenticatedJson(
    val parser: BodyParser[AnyContent],
    messagesApi: MessagesApi,
    builder: AuthenticatedBuilder[LoginSession]
  )(
    implicit val executionContext: ExecutionContext
  ) extends ActionBuilder[AuthMessagesRequest, AnyContent] {
    type ResultBlock[A] = (AuthMessagesRequest[A]) => Future[Result]

    @Inject
    def this (
      parser: BodyParsers.Default,
      messagesApi: MessagesApi,
      builder: UserAuthenticatedBuilderJson
    )(implicit ec: ExecutionContext) =
      this (parser: BodyParser[AnyContent], messagesApi, builder)

    def invokeBlock[A](request: Request[A], block: ResultBlock[A]): Future[Result] =
      builder.authenticate(request, { authRequest: AuthenticatedRequest[A, LoginSession] =>
        block(new AuthMessagesRequest[A](authRequest.user, messagesApi, request))
      })
  }

  class OptAuthenticated(
    val parser: BodyParser[AnyContent],
    messagesApi: MessagesApi,
    loginSessionRepo: LoginSessionRepo,
    conf: Configuration
  )(
    implicit val executionContext: ExecutionContext, db: Database
  ) extends ActionBuilder[MessagesRequest, AnyContent] {
    type ResultBlock[A] = (MessagesRequest[A]) => Future[Result]

    @Inject
    def this (
      parser: BodyParsers.Default,
      messagesApi: MessagesApi,
      loginSessionRepo: LoginSessionRepo,
      conf: Configuration
    )(implicit ec: ExecutionContext, db: Database) =
      this (parser: BodyParser[AnyContent], messagesApi, loginSessionRepo, conf)

    val needAuthenticationEntirely = conf.getOptional[Boolean]("need.authentication.entirely").getOrElse(false)

    def invokeBlock[A](request: Request[A], block: ResultBlock[A]): Future[Result] = {
      val login = try {
        loginSessionRepo.fromRequest(request)
      }
      catch {
        case t: Throwable =>
          Logger.error("Error in authentication.", t)
          throw t
      }
      login  match {
        case Some(ls) => {
          block(new MessagesRequest[A](request, messagesApi))
        }
        case None =>
          if (needAuthenticationEntirely) Future.successful(
            db.withConnection { implicit conn =>
              onUnauthorized(request)
            }
          )
          else block(new MessagesRequest[A](request, messagesApi))
      }
    }
  }
  
  def assumeUser(permitted: Boolean)(result: => Result): Result =
    if (permitted) result else Redirect(routes.HomeController.index)
  def assumeAdmin(login: LoginSession)(result: => Result): Result = assumeUser(login.isAdmin)(result)
}
