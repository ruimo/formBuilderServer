package helpers

import java.security.{MessageDigest, SecureRandom}

import com.google.common.primitives.Longs

import scala.util.Random

case class PasswordHash(value: Long) extends AnyVal

case class PasswordSalt(value: Long) extends AnyVal

object PasswordHash {
  val saltSource: SecureRandom = new SecureRandom

  def createToken() = saltSource.nextLong
  def createSalt() = PasswordSalt(createToken())

  def generate(password: String, salt: PasswordSalt = createSalt(), stretchCount: Int = 2000): PasswordHash = {
    val md = createShaEncoder
    for (_ <- 1 to stretchCount) {
      md.update(Longs.toByteArray(salt.value));
      md.update(password.getBytes("utf-8"))
    }
    PasswordHash(Longs.fromByteArray(md.digest()))
  }

  def createShaEncoder = MessageDigest.getInstance("SHA-256")

  def password(length: Int = 24): String =
    new Random(saltSource).alphanumeric.take(length).foldLeft(new StringBuilder)(_.append(_)).toString
}
