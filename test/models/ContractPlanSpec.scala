package models

import helpers.{InjectorSupport, PasswordHash, PasswordSalt}
import org.specs2.mutable._
import play.api.test.Helpers._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.Application
import play.api.db.Database
import play.api.Configuration
import com.ruimo.scoins.Scoping._

class ContractPlanSpec extends Specification with InjectorSupport {
  "Contract plan" should {
    "be able to create plan." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val repo = inject[ContractPlanRepo]
        val plan = repo.create("plan01", 10)
        doWith(repo.list().records) { list =>
          list.size === 1
          list(0) === plan
        }
      }
    }

    "be able to list plans." in {
      implicit val app: Application = GuiceApplicationBuilder().configure(inMemoryDatabase()).build()

      inject[Database].withConnection { implicit conn =>
        val repo = inject[ContractPlanRepo]
        val plan01 = repo.create("plan01", 10)
        val plan02 = repo.create("plan02", 5)
        val plan03 = repo.create("plan03", 8)
        doWith(repo.list().records) { list =>
          list.size === 3
          list(0) === plan01
          list(1) === plan02
          list(2) === plan03
        }

        doWith(repo.list(page = 0, pageSize = 2).records) { list =>
          list.size === 2
          list(0) === plan01
          list(1) === plan02
        }

        doWith(repo.list(page = 1, pageSize = 2).records) { list =>
          list.size === 1
          list(0) === plan03
        }

        doWith(repo.list(orderBy = OrderBy("contract_plan.max_form_format")).records) { list =>
          list.size === 3
          list(0) === plan02
          list(1) === plan03
          list(2) === plan01
        }

        repo.update(plan01.id.get, plan01.planName, plan01.maxFormFormat, deprecated = true)
        doWith(repo.list(includeDeprecated = true).records) { list =>
          list.size === 3
          list(0) === plan01.copy(deprecated = true)
          list(1) === plan02
          list(2) === plan03
        }

        doWith(repo.list(includeDeprecated = false).records) { list =>
          list.size === 2
          list(0) === plan02
          list(1) === plan03
        }
      }
    }
  }
}


