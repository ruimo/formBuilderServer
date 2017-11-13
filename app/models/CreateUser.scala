package models

import java.sql.Connection

import helpers.PasswordHash

case class CreateUser(
  userName: String,
  email: String,
  passwords: (String, String),
  role: Int
) {
  def create()(implicit conn: Connection) {
    val salt = PasswordHash.createSalt()

    User.create(userName, email, PasswordHash.generate(passwords._1, salt), salt, UserRole.byIndex(role))
  }

  def update(id: UserId)(implicit conn: Connection) {
    val salt = PasswordHash.createSalt()

    User.update(id, userName, email, PasswordHash.generate(passwords._1, salt), salt, UserRole.byIndex(role))
  }
}
