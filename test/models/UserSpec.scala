package models

import org.specs2.mutable._
import play.api.db.Databases
import play.api.test.Helpers
import play.api.test.WithApplication
import scalikejdbc.specs2.mutable.AutoRollback
import scalikejdbc._
import scalikejdbc.config._

class UserSpec extends Specification {
  "User" should {
    DBs.setup()

    "Can create new record." in new AutoRollback {
        val user = User.create("user0001", "email", PasswordHash(123L), PasswordSalt(234L))
        1 === 1
    }
  }
}
