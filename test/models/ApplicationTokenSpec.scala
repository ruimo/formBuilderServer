package models

import helpers.{InjectorSupport, PasswordHash, PasswordSalt}
import org.specs2.mutable._
import play.api.test.Helpers._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.Application
import play.api.db.Database
import play.api.Configuration
import com.ruimo.scoins.Scoping._

class ApplicationTokenSpec extends Specification with InjectorSupport {
  "Application token" should {
    "be able to create token." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val conf = inject[Configuration]
        val repo = new ApplicationTokenRepo(conf) {
          override def createToken(): Long = 123L
        }
        repo.maxApplicationTokenCountByUser === 5
        val user = User.create("testuser0001", "email", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        val token01 = repo.create(user.userId.get)
        token01.userId === user.userId.get
        token01.token === 123L

        doWith(repo.list(user.userId.get)) { list =>
          list.size === 1
          list(0) === token01
        }
      }
    }

    "duplicated token should be avoided." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val user = User.create("testuser0001", "email", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        val conf = inject[Configuration]
        val repo = new ApplicationTokenRepo(conf) {
          var index = -1
          val tokens = Seq(123L, 123L, 234L)
          override def createToken(): Long = {
            index += 1
            tokens(index)
          }
        }
        val token01 = repo.create(user.userId.get)
        token01.token === 123L

        val token02 = repo.create(user.userId.get)
        token02.token === 234L

        doWith(repo.list(user.userId.get)) { list =>
          list.size === 2
          list(0) === token02
          list(1) === token01
        }
      }
    }

    "creating more than maxApplicationTokenCountByUser should be avoided." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val conf = new Configuration(
          Configuration(
            "maxApplicationTokenCountByUser" -> 3
          ).underlying.withFallback(inject[Configuration].underlying)
        )
        val repo = new ApplicationTokenRepo(conf)
        repo.maxApplicationTokenCountByUser === 3
        val user = User.create("testuser0001", "email", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        val token01 = repo.create(user.userId.get)
        token01.userId === user.userId.get

        val token02 = repo.create(user.userId.get)
        token02.userId === user.userId.get

        val token03 = repo.create(user.userId.get)
        token03.userId === user.userId.get

        repo.create(user.userId.get) must throwA[MaxApplicationTokenException]
      }
    }
  }
}


