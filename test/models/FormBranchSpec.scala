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

class FormBranchSpec extends Specification with InjectorSupport {
  "Form branch" should {
    "can take record by branch." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val contractPlanRepo = inject[ContractPlanRepo]
        val formConfigRepo = inject[FormConfigRepo]
        val formBranchRepo = inject[FormBranchRepo]
        val contractedUserRepo = inject[ContractedUserRepo]

        val user1 = User.create("testuser0001", "email", PasswordHash(123L), PasswordSalt(234L), UserRole.ADMIN)
        val plan1 = contractPlanRepo.create("plan01", maxFormFormat = 5)
        val contractedUser1 = contractedUserRepo.create(
          plan1.id.get, user1.userId.get,
          contractFrom = Instant.parse("2007-12-03T10:15:30.00Z"),
          contractTo = Instant.parse("2007-12-03T10:15:30.00Z")
        )
        val his1 = formConfigRepo.create(
          contractedUser1.id.get,
          "conf01",
          FormConfigValue("{}")
        )

        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.STAGING) === None
        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.PROD) === None

        formBranchRepo.create(
          his1.id.get, FormBranchValue.STAGING
        )

        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.STAGING) === Some(his1)
        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.PROD) === None

        val his2 = formConfigRepo.create(
          contractedUser1.id.get,
          "conf01",
          FormConfigValue("{0}"),
          revision = FormConfigRevision(1)
        )

        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.STAGING) === Some(his1)
        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.PROD) === None

        formBranchRepo.create(
          his2.id.get, FormBranchValue.STAGING
        )

        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.STAGING) === Some(his2)
        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.PROD) === None

        val plan2= contractPlanRepo.create("plan02", maxFormFormat = 10)
        val contractedUser2 = contractedUserRepo.create(
          plan2.id.get, user1.userId.get,
          contractFrom = Instant.parse("2007-12-03T10:15:30.00Z"),
          contractTo = Instant.parse("2007-12-03T10:15:30.00Z")
        )
        val his2_1 = formConfigRepo.create(
          contractedUser2.id.get,
          "conf01",
          FormConfigValue("{}")
        )

        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.STAGING) === Some(his2)
        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.PROD) === None

        formBranchRepo.getLatest(contractedUser2.id.get, "conf01", FormBranchValue.STAGING) === None
        formBranchRepo.getLatest(contractedUser2.id.get, "conf01", FormBranchValue.PROD) === None

        formBranchRepo.create(
          his2_1.id.get, FormBranchValue.STAGING
        )

        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.STAGING) === Some(his2)
        formBranchRepo.getLatest(contractedUser1.id.get, "conf01", FormBranchValue.PROD) === None

        formBranchRepo.getLatest(contractedUser2.id.get, "conf01", FormBranchValue.STAGING) === Some(his2_1)
        formBranchRepo.getLatest(contractedUser2.id.get, "conf01", FormBranchValue.PROD) === None
      }
    }
  }
}

