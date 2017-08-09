package helpers

import java.security.MessageDigest
import com.google.common.primitives.Longs

case class PasswordHash(value: Long) extends AnyVal

case class PasswordSalt(value: Long) extends AnyVal

object PasswordHash {
  def generate(password: String, salt: PasswordSalt, stretchCount: Int = 2000): PasswordHash = {
    val md = createSha1Encoder
    for (_ <- 1 to stretchCount) {
      md.update(Longs.toByteArray(salt.value));
      md.update(password.getBytes("utf-8"))
    }
    PasswordHash(Longs.fromByteArray(md.digest()))
  }

  def createSha1Encoder = MessageDigest.getInstance("SHA-256")
}
