package filters

import javax.inject._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import models._
import akka.stream.Materializer

@Singleton
class LoginFilter @Inject() (
  loginSessionRepo: LoginSessionRepo,
  implicit val ec: ExecutionContext,
  implicit val mat: Materializer
) extends Filter {
  def apply(next: RequestHeader => Future[Result])
    (request: RequestHeader): Future[Result] = {
    implicit val req = request

    next(request).map { result =>
      result.session.get(loginSessionRepo.loginUserKey) match {
        case Some(s) => result.withSession(
          result.session + (loginSessionRepo.loginUserKey -> loginSessionRepo.extend(s))
        )
        case None => result
      }
    }
  }
}
