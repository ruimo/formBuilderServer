package models

import helpers.{InjectorSupport, PasswordHash, PasswordSalt}
import org.specs2.mutable._
import play.api.test.Helpers._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.Application
import play.api.db.Database

class UserSpec extends Specification with InjectorSupport {
  "User" should {
    "Same user name should be avoided." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        User.create("testuser0001", "email", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        ExceptionMapper.mapException {
          User.create("testuser0001", "email2", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        } must throwA[UniqueConstraintException]
      }
    }

    "Same email should be avoided." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        User.create("testuser0001", "email", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        ExceptionMapper.mapException {
          User.create("testuser0002", "email", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        } must throwA[UniqueConstraintException]
      }
    }
  }
}
