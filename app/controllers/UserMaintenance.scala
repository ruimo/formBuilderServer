package controllers

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
import play.api.i18n.Messages

@Singleton
class UserMaintenance @Inject()(
  cc: MessagesControllerComponents,
  parsers: PlayBodyParsers,
  loginSessionRepo: LoginSessionRepo,
  authenticated: Authenticated,
  fc: FormConstraints,
  db: Database
) extends MessagesAbstractController(cc) {
  def roleDropDown(implicit mp: PreferredMessagesProvider): Seq[(String, String)] = UserRole.all.map { r =>
    (r.ordinal.toString, Messages("userRole." + r.toString))
  }.toSeq

  def userForm(implicit mp: PreferredMessagesProvider) = Form(
    mapping(
      "userName" -> text.verifying(fc.userNameConstraint: _*),
      "email" -> text.verifying(fc.emailConstraint: _*),
      "passwords" -> tuple(
        "main" -> text.verifying(fc.passwordConstraint: _*),
        "confirm" -> text
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      ),
      "role" -> number(min = 0, max = UserRole.maxOrdinal())
    )(CreateUser.apply)(CreateUser.unapply)
  )

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    Ok(views.html.admin.userMaintenance())
  }

  def startCreate() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    
    NeedLogin.assumeAdmin(login) {
      Ok(views.html.admin.createUser(userForm, fc, roleDropDown, routes.UserMaintenance.create()))
    }
  }

  def create() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    
    NeedLogin.assumeAdmin(login) {
      userForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in UserMaintenance.create(): " + formWithErrors)
          BadRequest(views.html.admin.createUser(formWithErrors, fc, roleDropDown, routes.UserMaintenance.create()))
        },
        newUser => db.withConnection { implicit conn =>
          if (User.getByUserName(newUser.userName).isDefined) {
            BadRequest(
              views.html.admin.createUser(
                userForm.fill(newUser).withError("userName", Messages("duplicate")),
                fc, roleDropDown, routes.UserMaintenance.create()
              )
            )
          }
          else if (User.getByEmail(newUser.email).isDefined) {
            BadRequest(
              views.html.admin.createUser(
                userForm.fill(newUser).withError("email", Messages("duplicate")),
                fc, roleDropDown, routes.UserMaintenance.create()
              )
            )
          }
          else {
            try {
              ExceptionMapper.mapException {
                newUser.create()
                Redirect(
                  routes.UserMaintenance.index()
                ).flashing(
                  "message" -> Messages("created", Messages("user"))
                )
              }
            }
            catch {
              case e: UniqueConstraintException =>
                Logger.error("duplicated record.", e)
                BadRequest(
                  views.html.admin.createUser(
                    userForm.fill(newUser).withError("duplicate", Messages("duplicateRecord")),
                    fc, roleDropDown, routes.UserMaintenance.create()
                  )
                )
              case t: Throwable => throw t
            }
          }
        }
      )
    }
  }

  def edit(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        val orderBy = OrderBy(orderBySpec)
        Ok(
          views.html.admin.editUser(
            page, pageSize, orderBy,
            User.list(page, pageSize, orderBy)
          )
        )
      }
    }
  }

  def modifyStart(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        val user = User.byUserId(UserId(id))
        Ok(
          views.html.admin.modifyUser(
            userForm.fill(
              CreateUser(
                user.userName,
                user.email,
                ("", ""),
                user.userRole.ordinal()
              )
            ).discardingErrors,
            fc, roleDropDown, routes.UserMaintenance.modify(id)
          )
        )
      }
    }
  }

  def modify(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      userForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in UserMaintenance.modify(): " + formWithErrors)
          BadRequest(views.html.admin.modifyUser(formWithErrors, fc, roleDropDown, routes.UserMaintenance.modify(id)))
        },
        newUser => db.withConnection { implicit conn =>
          User.getByUserName(newUser.userName).flatMap { user =>
            if (user.userId.get.value != id)
              Some(
                BadRequest(
                  views.html.admin.modifyUser(
                    userForm.fill(newUser).withError("userName", Messages("duplicate")),
                    fc, roleDropDown, routes.UserMaintenance.modify(id)
                  )
                )
              )
            else None
          }.orElse {
            User.getByEmail(newUser.email).flatMap { user =>
              if (user.userId.get.value != id)
                Some(
                  BadRequest(
                    views.html.admin.modifyUser(
                      userForm.fill(newUser).withError("email", Messages("duplicate")),
                      fc, roleDropDown, routes.UserMaintenance.modify(id)
                    )
                  )
                )
              else None
            }
          } match {
            case Some(err) => err
            case None =>
              try {
                ExceptionMapper.mapException {
                  newUser.update(UserId(id))
                  Redirect(
                    routes.UserMaintenance.index()
                  ).flashing(
                    "message" -> Messages("updated", Messages("user"))
                  )
                }
              }
              catch {
                case e: UniqueConstraintException =>
                  Logger.error("duplicated record.", e)
                  BadRequest(
                    views.html.admin.modifyUser(
                      userForm.fill(newUser).withError("duplicate", Messages("duplicateRecord")),
                      fc, roleDropDown, routes.UserMaintenance.modify(id)
                    )
                  )
                case t: Throwable => throw t
              }
          }
        }
      )
    }
  }

  def delete(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        User.delete(UserId(id))
      }

      Redirect(
        routes.UserMaintenance.edit()
      ).flashing(
        "message" -> Messages("deleted", Messages("user"))
      )
    }
  }
}
