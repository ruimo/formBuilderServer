package models

import java.time.{Duration, Instant, LocalDate, ZoneOffset}

import play.api.Configuration
import helpers.PasswordHash
import java.sql.Connection

import scala.collection.{immutable => imm}
import javax.inject.{Inject, Singleton}

import anorm._

import scala.language.postfixOps

case class ApplicationTokenId(value: Long) extends AnyVal

case class ApplicationToken(
  id: Option[ApplicationTokenId],
  userId: UserId,
  token: Long,
  createdAt: Instant
)

@Singleton
class ApplicationTokenRepo @Inject() (
  conf: Configuration
) {
  val maxApplicationTokenCountByUser: Int = conf.getOptional[Int]("maxApplicationTokenCountByUser").getOrElse(5)
  val PeriodMargin = Duration.ofDays(7)
  val simple = {
    SqlParser.get[Option[Long]]("application_token.application_token_id") ~
    SqlParser.get[Long]("application_token.user_id") ~
    SqlParser.get[Long]("application_token.token") ~
    SqlParser.get[Instant]("application_token.created_at") map {
      case id~userId~token~createdAt =>
        ApplicationToken(
          id.map(ApplicationTokenId.apply), UserId(userId), token, createdAt
        )
    }
  }

  def createToken(): Long = PasswordHash.createToken()

  def create(userId: UserId)(implicit conn: Connection): ApplicationToken = {
    val tokenCount = SQL(
      "select count(*) from application_token where user_id = {id}"
    ).on(
      'id -> userId.value
    ).as(SqlParser.scalar[Int].single)
    if (tokenCount >= maxApplicationTokenCountByUser)
      throw new MaxApplicationTokenException(tokenCount + 1)

    val now = Instant.now()

    def createdToken(): Long = try {
      val token = createToken()
      ExceptionMapper.mapException {
        SQL(
          """
          insert into application_token (
            application_token_id, user_id, token, created_at
          ) values (
            (select nextval('application_token_seq')),
            {userId}, {token}, {createdAt}
          )
          """
        ).on(
          'userId -> userId.value,
          'token -> token,
          'createdAt -> now
        ).executeUpdate()
        token
      }
    } catch {
      case e: UniqueConstraintException => createdToken()
    }

    val token = createdToken()
    val id: Long = SQL("select currval('application_token_seq')").as(SqlParser.scalar[Long].single)
    
    ApplicationToken(Some(ApplicationTokenId(id)), userId, token, now)
  }

  def list(userId: UserId)(implicit conn: Connection): Seq[ApplicationToken] = SQL(
    """
    select * from application_token where user_id = {id} order by created_at desc
    """
  ).on(
    'id -> userId.value
  ).as(
    simple *
  )

  def isValid(
    contractedUserId: ContractedUserId, token: Long, now: LocalDate = LocalDate.now()
  )(implicit conn: Connection): Boolean = {
    val instant = now.atStartOfDay().toInstant(ZoneOffset.UTC)

    SQL(
      """
      select exists(
        select * from contracted_user cu
        inner join application_token at on at.user_id = cu.user_id
        where
          cu.contracted_user_id = {contractedUserId} and
          at.token = {token} and
          cu.contract_from <= {from}
       )
      """
    ).on(
      'contractedUserId -> contractedUserId.value,
      'token -> token,
      'from -> instant.plus(PeriodMargin),
      'to -> instant.minus(PeriodMargin)
    ).as(SqlParser.scalar[Boolean].single)
  }

  def remove(id: ApplicationTokenId)(implicit conn: Connection): Int = SQL(
    "delete from application_token where application_token_id = {id}"
  ).on(
    'id -> id.value
  ).executeUpdate()
}

