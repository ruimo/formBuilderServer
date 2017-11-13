package models

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import helpers.PasswordHash
import java.sql.Connection

import scala.collection.{immutable => imm}
import java.time.Instant

import anorm._

case class FormBranchId(value: Long) extends AnyVal

case class FormBranch(
  id: Option[FormBranchId],
  formConfigId: FormConfigId,
  branch: FormBranchValue
)

@Singleton
class FormBranchRepo @Inject() (
  formConfigRepo: FormConfigRepo
) {
  val simple = {
    SqlParser.get[Option[Long]]("form_branch.form_branch_id") ~
    SqlParser.get[Long]("form_branch_id.form_config_id") ~
    SqlParser.get[Int]("form_branch_id.branch_no") map {
      case id~formConfigId~branchNo =>
        FormBranch(
          id.map(FormBranchId.apply),
          FormConfigId(formConfigId),
          FormBranchValue.byIndex(branchNo)
        )
    }
  }

  def create(
    formConfigId: FormConfigId, branch: FormBranchValue
  )(implicit conn: Connection): FormBranch = ExceptionMapper.mapException {
    SQL(
      """
      insert into form_branch (
        form_branch_id, form_config_id, branch_no
      ) values (
        (select nextval('form_branch_seq')),
        {formConfigId}, {branchNo}
      )
      """
    ).on(
      'formConfigId -> formConfigId.value,
      'branchNo -> branch.ordinal
    ).executeUpdate()

    val id: Long = SQL("select currval('form_branch_seq')").as(SqlParser.scalar[Long].single)
    
    FormBranch(Some(FormBranchId(id)), formConfigId, branch)
  }

  def getLatest(
    contractedUserId: ContractedUserId, configName: String, branch: FormBranchValue
  )(implicit conn: Connection): Option[FormConfig] = SQL(
    """
    select * from form_config
    inner join form_branch on form_config.form_config_id = form_branch.form_config_id
    where
      form_config.contracted_user_id = {contractedUserId} and
      form_config.config_name = {configName} and
      form_branch.branch_no = {branchNo}
    order by form_config.created_at desc
    limit 1
    """
  ).on(
    'contractedUserId -> contractedUserId.value,
    'configName -> configName,
    'branchNo -> branch.ordinal
  ).as(
    formConfigRepo.simple.singleOpt
  )
}
