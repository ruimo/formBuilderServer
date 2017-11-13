package models

import java.time.Instant

import helpers.{InjectorSupport, PasswordHash, PasswordSalt}
import org.specs2.mutable._
import play.api.test.Helpers._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.Application
import play.api.db.Database
import play.api.Configuration
import com.ruimo.scoins.Scoping._

class FormConfigSpec extends Specification with InjectorSupport {
  "Form config" should {
    "can list when record empty." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val contractPlanRepo = inject[ContractPlanRepo]
        val formConfigRepo = inject[FormConfigRepo]
        val contractedUserRepo = inject[ContractedUserRepo]

        val user1 = User.create("testuser0001", "email", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        val plan1 = contractPlanRepo.create("plan01", maxFormFormat = 5)
        val contractedUser1 = contractedUserRepo.create(
          plan1.id.get, user1.userId.get,
          contractFrom = Instant.parse("2007-12-03T10:15:30.00Z"),
          contractTo = Instant.parse("2007-12-03T10:15:30.00Z")
        )
        formConfigRepo.list(orderBy = OrderBy("fc0.config_name"), contractedUserId = contractedUser1.id.get).records.size === 0
      }
    }

    "can list by plan." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val contractPlanRepo = inject[ContractPlanRepo]
        val formConfigRepo = inject[FormConfigRepo]
        val contractedUserRepo = inject[ContractedUserRepo]

        val user1 = User.create("testuser0001", "email", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        val plan1 = contractPlanRepo.create("plan01", maxFormFormat = 5)
        val plan2 = contractPlanRepo.create("plan02", maxFormFormat = 10)
        val contractedUser1 = contractedUserRepo.create(
          plan1.id.get, user1.userId.get,
          contractFrom = Instant.parse("2007-12-03T10:15:30.00Z"),
          contractTo = Instant.parse("2007-12-03T10:15:30.00Z")
        )
        val contractedUser2 = contractedUserRepo.create(
          plan2.id.get, user1.userId.get,
          contractFrom = Instant.parse("2007-12-03T10:15:30.00Z"),
          contractTo = Instant.parse("2007-12-03T10:15:30.00Z")
        )
        val config01 = formConfigRepo.create(
          contractedUser1.id.get, "config01", FormConfigValue("{}")
        )

        doWith(
          formConfigRepo.list(
            orderBy = OrderBy("fc0.config_name"), contractedUserId = contractedUser1.id.get
          ).records
        ) { records =>
          records.size === 1
          records(0) === config01
        }
        formConfigRepo.list(orderBy = OrderBy("fc0.config_name"), contractedUserId = contractedUser2.id.get).records.size === 0
      }
    }

    "can list lates revision." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val contractPlanRepo = inject[ContractPlanRepo]
        val formConfigRepo = inject[FormConfigRepo]
        val contractedUserRepo = inject[ContractedUserRepo]

        val user1 = User.create("testuser0001", "email", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        val plan1 = contractPlanRepo.create("plan01", maxFormFormat = 5)
        val plan2 = contractPlanRepo.create("plan02", maxFormFormat = 10)
        val contractedUser1 = contractedUserRepo.create(
          plan1.id.get, user1.userId.get,
          contractFrom = Instant.parse("2007-12-03T10:15:30.00Z"),
          contractTo = Instant.parse("2007-12-03T10:15:30.00Z")
        )
        val contractedUser2 = contractedUserRepo.create(
          plan2.id.get, user1.userId.get,
          contractFrom = Instant.parse("2007-12-03T10:15:30.00Z"),
          contractTo = Instant.parse("2007-12-03T10:15:30.00Z")
        )
        val config01_0 = formConfigRepo.create(
          contractedUser1.id.get, "config01", FormConfigValue("{}")
        )
        val config01_1 = formConfigRepo.create(
          contractedUser1.id.get, "config01", FormConfigValue("{a}"), config01_0.revision.next
        )

        doWith(
          formConfigRepo.list(
            orderBy = OrderBy("fc0.config_name"), contractedUserId = contractedUser1.id.get
          ).records
        ) { records =>
          records.size === 1
          records(0) === config01_1
        }
        formConfigRepo.list(orderBy = OrderBy("fc0.config_name"), contractedUserId = contractedUser2.id.get).records.size === 0
      }
    }

    "can not duplicate records having the same revision." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val contractPlanRepo = inject[ContractPlanRepo]
        val formConfigRepo = inject[FormConfigRepo]
        val contractedUserRepo = inject[ContractedUserRepo]

        val user1 = User.create("testuser0001", "email", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        val plan1 = contractPlanRepo.create("plan01", maxFormFormat = 5)
        val plan2 = contractPlanRepo.create("plan02", maxFormFormat = 10)
        val contractedUser1 = contractedUserRepo.create(
          plan1.id.get, user1.userId.get,
          contractFrom = Instant.parse("2007-12-03T10:15:30.00Z"),
          contractTo = Instant.parse("2007-12-03T10:15:30.00Z")
        )
        val config01_0 = formConfigRepo.create(
          contractedUser1.id.get, "config01", FormConfigValue("{}")
        )

        formConfigRepo.create(
          contractedUser1.id.get, "config01", FormConfigValue("{a}"), config01_0.revision
        ) must throwA[UniqueConstraintException]
      }
    }
  }
}
