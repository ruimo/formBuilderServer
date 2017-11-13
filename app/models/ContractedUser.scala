package models

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import helpers.PasswordHash
import java.sql.Connection

import scala.collection.{immutable => imm}
import java.time.Instant

import anorm._

case class ContractedUserId(value: Long) extends AnyVal

case class ContractedUser(
  id: Option[ContractedUserId],
  contractPlanId: ContractPlanId,
  userId: UserId,
  contractFrom: Instant,
  contractTo: Instant
)

@Singleton
class ContractedUserRepo @Inject() (
  contractPlanRepo: ContractPlanRepo
) {
  val simple = {
    SqlParser.get[Option[Long]]("contracted_user.contracted_user_id") ~
    SqlParser.get[Long]("contracted_user.contract_plan_id") ~
    SqlParser.get[Long]("contracted_user.user_id") ~
    SqlParser.get[Instant]("contracted_user.contract_from") ~
    SqlParser.get[Instant]("contracted_user.contract_to") map {
      case id~contractPlanId~userId~contractFrom~contractTo =>
        ContractedUser(
          id.map(ContractedUserId.apply), ContractPlanId(contractPlanId), UserId(userId), contractFrom, contractTo
        )
    }
  }

  def create(
    contractPlanId: ContractPlanId, userId: UserId, contractFrom: Instant, contractTo: Instant
  )(implicit conn: Connection): ContractedUser = {
    ExceptionMapper.mapException {
      SQL(
        """
        insert into contracted_user (
          contracted_user_id, contract_plan_id, user_id, contract_from, contract_to
        ) values (
          (select nextval('contracted_user_seq')), {contractPlanId}, {userId}, {contractFrom}, {contractTo}
        )
        """
      ).on(
        'contractPlanId -> contractPlanId.value,
        'userId -> userId.value,
        'contractFrom -> contractFrom,
        'contractTo -> contractTo
      ).executeUpdate()

      val id: Long = SQL("select currval('contracted_user_seq')").as(SqlParser.scalar[Long].single)

      ContractedUser(Some(ContractedUserId(id)), contractPlanId, userId, contractFrom, contractTo)
    }
  }

  def update(
    contractPlanId: ContractPlanId, userId: UserId, contractFrom: Instant, contractTo: Instant
  )(implicit conn: Connection): Int = SQL(
    """
    update contracted_user set
      contract_from = {contractFrom},
      contract_to = {contractTo}
    where contract_plan_id = {contractPlanId} and user_id = {userId}
    """
  ).on(
    'contractFrom -> contractFrom,
    'contractTo -> contractTo,
    'contractPlanId -> contractPlanId.value,
    'userId -> userId.value
  ).executeUpdate()

  val withUserAndContractPlan =
    simple ~ User.simple ~ contractPlanRepo.simple map {
      case contractedUser~user~plan => (user, plan, contractedUser)
    }

  def list(
    page: Int = 0, pageSize: Int = 10, orderBy: OrderBy = OrderBy("users.user_name, contracted_user.contract_from"),
    byUser: Option[UserId] = None, byPlan: Option[ContractPlanId] = None
  )(implicit conn: Connection): PagedRecords[(User, ContractPlan, ContractedUser)] = {
    import scala.language.postfixOps

    val where = 
      byUser.map { uid =>
        " and users.user_id = " + uid.value + " "
      }.getOrElse("") +
      byPlan.map { pid =>
        " and contract_plan.contract_plan_id = " + pid.value + " "
      }.getOrElse("")

    val records = SQL(
      s"""
      select * from contracted_user
      inner join contract_plan on contract_plan.contract_plan_id = contracted_user.contract_plan_id
      inner join users on users.user_id = contracted_user.user_id
      where 1 = 1
      $where
      order by $orderBy limit {pageSize} offset {offset}
      """
    ).on(
      'pageSize -> pageSize,
      'offset -> page * pageSize
    ).as(
      withUserAndContractPlan *
    )

    val count = SQL(
      s"""
      select count(*) from contracted_user
      inner join contract_plan on contract_plan.contract_plan_id = contracted_user.contract_plan_id
      inner join users on users.user_id = contracted_user.user_id
      where 1 = 1 $where
      """
    ).as(SqlParser.scalar[Long].single)
    
    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, records)
  }

  def delete(planId: ContractPlanId, userId: UserId)(implicit conn: Connection): Int = {
    SQL(
      "delete from contracted_user where contract_plan_id = {planId} and user_id = {userId}"
    ).on(
      'planId -> planId.value,
      'userId -> userId.value
    ).executeUpdate()
  }
}
