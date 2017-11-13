package models

import play.api.Configuration
import javax.inject.{Inject, Singleton}

import play.api.db.Database
import play.api.mvc.RequestHeader

case class LoginSession(user: User, expireTime: Long) {
  val isAdmin: Boolean = user.userRole == UserRole.ADMIN
}

@Singleton
class LoginSessionRepo @Inject() (
  conf: Configuration,
  db: Database
) {
  val loginUserKey = "loginUser"
  val sessionTimeout = conf.getOptional[Int]("login.timeout.minute").getOrElse(5) * 60 * 1000

  def serialize(storeUserId: Long, expirationTime: Long): String = storeUserId + ";" + expirationTime

  def extend(sessionString: String): String = {
    val args = sessionString.split(';').map(_.toLong)
    args(0) + ";" + (System.currentTimeMillis + sessionTimeout)
  }

  def apply(sessionString: String): LoginSession = {
    val args = sessionString.split(';').map(_.toLong)
    val user = db.withConnection { implicit conn => User.byUserId(UserId(args(0)))}
    LoginSession(user, args(1))
  }

  def get(sessionString: String): Option[LoginSession] = {
    val args = sessionString.split(';').map(_.toLong)
    db.withConnection { implicit conn =>
      User.getByUserId(UserId(args(0))).map { user =>
        LoginSession(user, args(1))
      }
    }
  }

  def fromRequest(
    request: RequestHeader, now: Long = System.currentTimeMillis
  ): Option[LoginSession] = {
    val login = request.session.get(loginUserKey)
    login.flatMap { sessionString =>
      get(sessionString).flatMap { login =>
        if (login.expireTime < now) None else Some(login)
      }
    }
  }
}
