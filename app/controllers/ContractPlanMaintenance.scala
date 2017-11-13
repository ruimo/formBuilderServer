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
class ContractPlanMaintenance @Inject()(
  cc: MessagesControllerComponents,
  parsers: PlayBodyParsers,
  loginSessionRepo: LoginSessionRepo,
  authenticated: Authenticated,
  db: Database,
  implicit val contractPlanRepo: ContractPlanRepo
) extends MessagesAbstractController(cc) {
  val contractPlanForm = Form(
    mapping(
      "planName" -> text.verifying(nonEmpty, maxLength(256)),
      "maxFormFormat" -> number(min = 1),
      "deprecated" -> boolean
    )(CreateContractPlan.apply)(CreateContractPlan.unapply)
  )

  def index = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    Ok(views.html.admin.contractPlanMaintenance())
  }

  def startCreate() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    
    NeedLogin.assumeAdmin(login) {
      Ok(views.html.admin.createContractPlan(contractPlanForm, routes.ContractPlanMaintenance.create()))
    }
  }

  def create() = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    NeedLogin.assumeAdmin(login) {
      contractPlanForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ContractPlanMaintenance.create(): " + formWithErrors)
          BadRequest(views.html.admin.createContractPlan(formWithErrors, routes.ContractPlanMaintenance.create()))
        },
        newPlan => db.withConnection { implicit conn =>
          try {
            newPlan.save()
            Redirect(
              routes.ContractPlanMaintenance.startCreate()
            ).flashing(
              "message" -> Messages("created", Messages("contractPlan"))
            )
          }
          catch {
            case e: UniqueConstraintException =>
              Logger.error("duplicated record.", e)
              BadRequest(
                views.html.admin.createContractPlan(
                  contractPlanForm.fill(newPlan).withError("duplicate", Messages("duplicateRecord")),
                  routes.ContractPlanMaintenance.create()
                )
              )
            case t: Throwable => throw t
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
          views.html.admin.editContractPlan(
            page, pageSize, orderBy,
            contractPlanRepo.list(page, pageSize, orderBy, includeDeprecated = true)
          )
        )
      }
    }
  }

  def modifyStart(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login
    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        val plan = contractPlanRepo.byPlanId(ContractPlanId(id))
        Ok(
          views.html.admin.modifyContractPlan(
            contractPlanForm.fill(
              CreateContractPlan(
                plan.planName,
                plan.maxFormFormat,
                plan.deprecated
              )
            ).discardingErrors,
            routes.ContractPlanMaintenance.modify(id)
          )
        )
      }
    }
  }

  def modify(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      contractPlanForm.bindFromRequest.fold(
        formWithErrors => {
          Logger.error("Validation error in ContractPlanMaintenance.modify(): " + formWithErrors)
          BadRequest(views.html.admin.modifyContractPlan(formWithErrors, routes.ContractPlanMaintenance.modify(id)))
        },
        newPlan => db.withConnection { implicit conn =>
          try {
            newPlan.update(ContractPlanId(id))
            Redirect(
              routes.ContractPlanMaintenance.edit()
            ).flashing(
              "message" -> Messages("updated", Messages("contractPlan"))
            )
          }
          catch {
            case e: UniqueConstraintException =>
              Logger.error("duplicated record.", e)
              BadRequest(
                views.html.admin.modifyContractPlan(
                  contractPlanForm.fill(newPlan).withError("duplicate", Messages("duplicateRecord")),
                  routes.ContractPlanMaintenance.modify(id)
                )
              )
            case t: Throwable => throw t
          }
        }
      )
    }
  }

  def delete(id: Long) = authenticated { implicit request: AuthMessagesRequest[AnyContent] =>
    implicit val login: LoginSession = request.login

    NeedLogin.assumeAdmin(login) {
      db.withConnection { implicit conn =>
        contractPlanRepo.delete(ContractPlanId(id))
      }

      Redirect(
        routes.ContractPlanMaintenance.edit()
      ).flashing(
        "message" -> Messages("deleted", Messages("contractPlan"))
      )
    }
  }
}
