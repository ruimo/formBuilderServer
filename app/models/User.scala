package models

import java.sql.Connection

import scala.collection.{immutable => imm}
import java.time.Instant

import anorm._
import helpers.{PasswordHash, PasswordSalt}
import scala.language.postfixOps

case class UserId(value: Long) extends AnyVal

case class User(
  userId: Option[UserId],
  userName: String,
  email: String,
  hash: PasswordHash,
  salt: PasswordSalt,
  userRole: UserRole,
  createdAt: Instant
) {
  def isPasswordValid(password: String): Boolean = {
    PasswordHash.generate(password, salt).value == hash.value
  }
}

object User {
  val FirstUserName = "administrator"
  val simple = {
    SqlParser.get[Option[Long]]("users.user_id") ~
    SqlParser.get[String]("users.user_name") ~
    SqlParser.get[String]("users.email") ~
    SqlParser.get[Long]("users.hash") ~
    SqlParser.get[Long]("users.salt") ~
    SqlParser.get[Int]("users.user_role") ~
    SqlParser.get[Instant]("users.created_at") map {
      case userId~userName~email~hash~salt~role~createdAt =>
        User(
          userId.map(UserId.apply),
          userName,
          email,
          PasswordHash(hash),
          PasswordSalt(salt),
          UserRole.byIndex(role),
          createdAt
        )
    }
  }

  def byUserId(id: UserId)(implicit conn: Connection): User = SQL(
    "select * from users where user_id={id}"
  ).on(
    'id -> id.value
  ).as(
    simple.single
  )

  def getByUserId(id: UserId)(implicit conn: Connection): Option[User] = SQL(
    "select * from users where user_id={id}"
  ).on(
    'id -> id.value
  ).as(
    simple.singleOpt
  )

  def getByUserName(userName: String)(implicit conn: Connection): Option[User] = SQL(
    "select * from users where user_name={userName}"
  ).on(
    'userName -> userName
  ).as(
    simple.singleOpt
  )

  def getByEmail(email: String)(implicit conn: Connection): Option[User] = SQL(
    "select * from users where email={email}"
  ).on(
    'email -> email
  ).as(
    simple.singleOpt
  )

  def create(
    userName: String, email: String, hash: PasswordHash, salt: PasswordSalt, userRole: UserRole
  )(implicit conn: Connection): User = {
    val now = Instant.now()
    SQL(
      """
      insert into users (
        user_id, user_name, email, hash, salt, created_at, user_role
      ) values (
        (select nextval('users_seq')),
        {userName}, {email}, {hash}, {salt}, {now}, {userRole}
       )
      """
    ).on(
      'userName -> userName,
      'email -> email,
      'hash -> hash.value,
      'salt -> salt.value,
      'now -> now,
      'userRole -> userRole.ordinal()
    ).executeUpdate()

    val id: Long = SQL("select currval('users_seq')").as(SqlParser.scalar[Long].single)

    User(Some(UserId(id)), userName, email, hash, salt, userRole, now)
  }

  def count(implicit conn: Connection): Long =
    SQL("select count(*) from users").as(SqlParser.scalar[Long].single)

  def login(
    userName: String, password: String
  )(implicit conn: Connection): Option[User] = {
    val user: Option[User] = SQL("select * from users where user_name = {userName}").on('userName -> userName).as(simple.singleOpt)
    user.filter { _.isPasswordValid(password) }
  }

  def createFirstUser(password: String)(implicit conn: Connection): User = {
    val salt = PasswordHash.createSalt()
    create(FirstUserName, "", PasswordHash.generate(password, salt), salt, UserRole.ADMIN)
  }

  def list(
    page: Int, pageSize: Int, orderBy: OrderBy
  )(implicit conn: Connection): PagedRecords[User] = {
    val records = SQL(
      s"""
      select
        *
      from users order by $orderBy limit {pageSize} offset {offset}
      """
    ).on(
      'pageSize -> pageSize,
      'offset -> page * pageSize
    ).as(
      simple *
    )

    val count = SQL("select count(*) from users").as(SqlParser.scalar[Long].single)

    PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, records)
  }

  def update(
    id: UserId, userName: String, email: String, hash: PasswordHash, salt: PasswordSalt, userRole: UserRole
  )(implicit conn: Connection) {
    SQL(
      """
      update users set
        user_name = {userName},
        email = {email},
        hash = {hash},
        salt = {salt},
        user_role = {userRole}
      where user_id = {id}
      """
    ).on(
      'id -> id.value,
      'userName -> userName,
      'email -> email,
      'hash -> hash.value,
      'salt -> salt.value,
      'userRole -> userRole.ordinal()
    ).executeUpdate()
  }

  def delete(id: UserId)(implicit conn: Connection): Int = {
    SQL(
      "delete from users where user_id = {id}"
    ).on(
      'id -> id.value
    ).executeUpdate()
  }
}
