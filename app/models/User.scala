package models

import helpers.{PasswordHash, PasswordSalt}
import scalikejdbc._

case class UserId(value: Long) extends AnyVal

case class User(
  userId: UserId,
  userName: String,
  email: String,
  hash: PasswordHash,
  salt: PasswordSalt,
  createdAt: Long
) {
  def isPasswordValid(password: String): Boolean = {
    PasswordHash.generate(password, salt).value == hash.value
  }
}

object User extends SQLSyntaxSupport[User] {
  override val tableName = "users"
  override val columns = Seq("user_id", "user_name", "email", "hash", "salt", "created_at")

  def apply(rs: WrappedResultSet) = new User(
    UserId(rs.long("user_id")),
    rs.string("user_name"),
    rs.string("email"),
    PasswordHash(rs.long("hash")),
    PasswordSalt(rs.long("salt")),
    rs.timestamp("created_at").getTime
  )

  def create(
    userName: String, email: String, hash: PasswordHash, salt: PasswordSalt
  )(implicit session: DBSession = autoSession): User = {
    val now = System.currentTimeMillis
    sql"""insert into users (
      user_id, user_name, email, hash, salt, created_at
    ) values (
      (select nextval('users_seq')),
      ${userName}, ${email}, ${hash.value}, ${salt.value}, ${now}
    )
    """.update.apply()

    val id: Long = sql"""select currval('users_seq')""".map(_.long(1)).single.apply().get

    User(UserId(id), userName, email, hash, salt, now)
  }

  def isValidUser(
    userName: String, password: String
  )(implicit session: DBSession = autoSession): Boolean = {
    sql"""select * from users where user_name = ${userName}"""
      .map(rs => User(rs))
      .single
      .apply()
      .map { _.isPasswordValid(password) }
      .getOrElse(false)
  }
}
