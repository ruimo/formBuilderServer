package models

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import helpers.PasswordHash
import java.sql.Connection

import scala.collection.{immutable => imm}
import java.time.Instant

import anorm._

case class FormConfigId(value: Long) extends AnyVal

case class FormConfigRevision private (value: Long) extends AnyVal {
  def next: FormConfigRevision = FormConfigRevision(value + 1)
}

object FormConfigRevision {
  val Zero = FormConfigRevision(0)
  def apply(value: Long): FormConfigRevision = {
    if (value == 0) Zero else new FormConfigRevision(value)
  }
}

case class FormConfigValue(value: String) extends AnyVal

case class FormConfig(
  id: Option[FormConfigId],
  contractedUserId: ContractedUserId,
  configName: String,
  revision: FormConfigRevision,
  config: FormConfigValue,
  comment: String,
  createdAt: Instant
)

@Singleton
class FormConfigRepo @Inject() (
) {
  val simple = {
    SqlParser.get[Option[Long]]("form_config.form_config_id") ~
    SqlParser.get[Long]("form_config.contracted_user_id") ~
    SqlParser.get[String]("form_config.config_name") ~
    SqlParser.get[Long]("form_config.revision") ~
    SqlParser.get[String]("form_config.config") ~
    SqlParser.get[String]("form_config.comment") ~
    SqlParser.get[Instant]("form_config.created_at") map {
      case id~contractedUserId~configName~revision~config~comment~createdAt =>
        FormConfig(
          id.map(FormConfigId.apply),
          ContractedUserId(contractedUserId),
          configName,
          FormConfigRevision(revision),
          FormConfigValue(config),
          comment,
          createdAt
        )
    }
  }

  def create(
    contractedUserId: ContractedUserId, configName: String, config: FormConfigValue,
    revision: FormConfigRevision = FormConfigRevision.Zero, comment: String = "",
    now: Instant = Instant.now()
  )(implicit conn: Connection): FormConfig = ExceptionMapper.mapException {
    SQL(
      """
      insert into form_config (
        form_config_id, contracted_user_id, config_name, revision, config, comment, created_at
      ) values (
        (select nextval('form_config_seq')),
        {contractedUserId}, {configName}, {revision}, {config}, {comment}, {now}
      )
      """
    ).on(
      'contractedUserId -> contractedUserId.value,
      'configName -> configName,
      'revision -> revision.value,
      'config -> config.value,
      'comment -> comment,
      'now -> now
    ).executeUpdate()

    val id: Long = SQL("select currval('form_config_seq')").as(SqlParser.scalar[Long].single)

    FormConfig(Some(FormConfigId(id)), contractedUserId, configName, revision, config, comment, now)
  }

  def list(
    page: Int = 0, pageSize: Int = 10, orderBy: OrderBy, contractedUserId: ContractedUserId
  )(implicit conn: Connection): PagedRecords[FormConfig] = {
    import scala.language.postfixOps
    
    val where = """
      where
        fc0.contracted_user_id = {contractedUserId} and
        fc0.revision = (
          select max(fc1.revision) from form_config fc1
          where
            fc1.contracted_user_id = {contractedUserId} and
            fc0.config_name = fc1.config_name
        )
    """

    val records = SQL(
      s"""
      select * from form_config fc0
      $where
      order by $orderBy limit {pageSize} offset {offset}
      """
    ).on(
      'contractedUserId -> contractedUserId.value,
      'pageSize -> pageSize,
      'offset -> page * pageSize
    ).as(
      simple *
    )

    val count = SQL(
      s"""
      select count(*) from form_config fc0
      $where
      """
    ).on(
      'contractedUserId -> contractedUserId.value
    ).as(SqlParser.scalar[Long].single)

    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, records)
  }
}
