package controllers

import java.time.ZoneOffset
import java.time.LocalDateTime
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
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
import play.api.i18n.Messages
import play.api.i18n.{Messages, MessagesProvider}

@Singleton
class ContractedUserMaintenance @Inject()(
  cc: MessagesControllerComponents,
  parsers: PlayBodyParsers,
  loginSessionRepo: LoginSessionRepo,
  authenticated: Authenticated,
  db: Database,
  contractedUserRepo: ContractedUserRepo,
  contractPlanRepo: ContractPlanRepo
) extends MessagesAbstractController(cc) {
  def contractDateFormat(implicit mp: MessagesProvider) = Messages("contractDateFormat")
  def dateTimeFormatter(implicit mp: MessagesProvider) = DateTimeFormatter.ofPattern(contractDateFormat)

  def createForm(implicit mp: PreferredMessagesProvider) = Form(
    mapping(
      "planId" -> longNumber,
      "userId" -> longNumber,
      "contractFrom" -> localDate(contractDateFormat),
      "contractTo" -> localDate(contractDateFormat)
    )(CreateContractedUser.apply)(CreateContractedUser.unapply)
  )

  def edit() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      Ok(views.html.admin.editContractedUser(createForm, contractDateFormat))
    }
  }

  def list(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      Ok(
        views.html.admin.contractedUserList(
          db.withConnection { implicit conn =>
            contractedUserRepo.list(
              page, pageSize, OrderBy(orderBySpec)
            )
          },
          dateTimeFormatter
        )
      )
    }
  }

  def delete(planId: Long, userId: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        contractedUserRepo.delete(ContractPlanId(planId), UserId(userId))
      }

      Redirect(
        routes.ContractedUserMaintenance.edit()
      ).flashing(
        "message" -> Messages("deleted", Messages("contractedUser"))
      )
    }
  }

  def selectContractPlan(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      Ok(
        views.html.admin.selectContractPlan(
          db.withConnection { implicit conn =>
            contractPlanRepo.list(page, pageSize, OrderBy(orderBySpec))
          }
        )
      )
    }
  }

  def selectUser(
    page: Int, pageSize: Int, orderBySpec: String
  ) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      Ok(
        views.html.admin.selectUser(
          db.withConnection { implicit conn =>
            User.list(page, pageSize, OrderBy(orderBySpec))
          }
        )
      )
    }
  }

  def create() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      createForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ContractedUserMaintenance.create(): " + formWithErrors)
          BadRequest(
            views.html.admin.changeContractedUser(formWithErrors)
          )
        },
        newContractedUsr => db.withConnection { implicit conn =>
          contractedUserRepo.create(
            ContractPlanId(newContractedUsr.planId),
            UserId(newContractedUsr.userId),
            newContractedUsr.contractFrom.atStartOfDay().toInstant(ZoneOffset.UTC),
            newContractedUsr.contractTo.atStartOfDay().toInstant(ZoneOffset.UTC)
          )
          Ok(views.html.admin.changeContractedUser(createForm))
        }
      )
    }
  }
}
