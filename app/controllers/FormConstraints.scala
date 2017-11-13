package controllers

import java.util.regex.Pattern

import scala.util.matching.Regex
import play.api.data.validation.Constraint
import play.api.data.validation.Constraints._
import play.api.data.validation.{Invalid, Valid, ValidationError}
import javax.inject.Inject
import javax.inject.Singleton

import play.api.Configuration

@Singleton
class FormConstraints @Inject() (
  conf: Configuration
) {
  def passwordMinLength: Int = conf.getOptional[Int]("password.min.length").getOrElse(8)
  def passwordMaxLength = 24
  val userNameMinLength = 6
  val userNameMaxLength = 24
  def userNameConstraint: Seq[Constraint[String]] =
    Seq(minLength(userNameMinLength), maxLength(userNameMaxLength))
  def passwordCharConstraint: Constraint[String] = Constraint[String]("constraint.password.char") { s =>
    if (s.forall(c => (0x21 <= c && c < 0x7e))) Valid else Invalid(ValidationError("error.pasword.char"))
  }
  val passwordConstraint = List(minLength(passwordMinLength), maxLength(passwordMaxLength), passwordCharConstraint)
  val emailMaxLength = 255
  val emailConstraint = List(nonEmpty, maxLength(emailMaxLength))
}
