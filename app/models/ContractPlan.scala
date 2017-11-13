package models

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import helpers.PasswordHash
import java.sql.Connection

import scala.collection.{immutable => imm}
import java.time.Instant

import anorm._

case class ContractPlanId(value: Long) extends AnyVal

case class ContractPlan(
  id: Option[ContractPlanId],
  planName: String,
  maxFormFormat: Int,
  deprecated: Boolean
)

@Singleton
class ContractPlanRepo @Inject() (
) {
  val simple = {
    SqlParser.get[Option[Long]]("contract_plan.contract_plan_id") ~
    SqlParser.get[String]("contract_plan.plan_name") ~
    SqlParser.get[Int]("contract_plan.max_form_format") ~
    SqlParser.get[Boolean]("contract_plan.deprecated") map {
      case id~planName~maxFormFormat~deprecated =>
        ContractPlan(
          id.map(ContractPlanId.apply), planName, maxFormFormat, deprecated
        )
    }
  }

  def byPlanId(id: ContractPlanId)(implicit conn: Connection): ContractPlan = SQL(
    "select * from contract_plan where contract_plan_id={id}"
  ).on(
    'id -> id.value
  ).as(
    simple.single
  )

  def create(planName: String, maxFormFormat: Int)(implicit conn: Connection): ContractPlan = {
    ExceptionMapper.mapException {
      SQL(
        """
        insert into contract_plan (
          contract_plan_id, plan_name, max_form_format, deprecated
        ) values (
          (select nextval('contract_plan_seq')),
          {planName}, {maxFormFormat}, false
        )
        """
      ).on(
        'planName -> planName,
        'maxFormFormat -> maxFormFormat
      ).executeUpdate()

      val id: Long = SQL("select currval('contract_plan_seq')").as(SqlParser.scalar[Long].single)

      ContractPlan(Some(ContractPlanId(id)), planName, maxFormFormat, false)
    }
  }

  def delete(id: ContractPlanId)(implicit conn: Connection): Int = {
    SQL(
      "delete from contract_plan where contract_plan_id = {id}"
    ).on(
      'id -> id.value
    ).executeUpdate()
  }

  def update(
    id: ContractPlanId, planName: String, maxFormFormat: Int, deprecated: Boolean
  )(implicit conn: Connection): Int = ExceptionMapper.mapException {
    SQL(
      """
      update contract_plan set
        plan_name = {planName},
        max_form_format = {maxFormFormat},
        deprecated = {deprecated}
      where contract_plan_id = {id}
      """
    ).on(
      'id -> id.value,
      'planName -> planName,
      'maxFormFormat -> maxFormFormat,
      'deprecated -> deprecated
    ).executeUpdate()
  }

  def list(
    page: Int = 0, pageSize: Int = 10, orderBy: OrderBy = OrderBy("contract_plan.plan_name"),
    includeDeprecated: Boolean = false
  )(implicit conn: Connection): PagedRecords[ContractPlan] = {
    import scala.language.postfixOps

    val records = SQL(
      """
      select * from contract_plan
      """ + (
        if (includeDeprecated) "" else "where deprecated = false"
      ) + s"""
      order by $orderBy limit {pageSize} offset {offset}
      """
    ).on(
      'pageSize -> pageSize,
      'offset -> page * pageSize
    ).as(
      simple *
    )

    val count = SQL("select count(*) from contract_plan").as(SqlParser.scalar[Long].single)

    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, records)
  }
}
