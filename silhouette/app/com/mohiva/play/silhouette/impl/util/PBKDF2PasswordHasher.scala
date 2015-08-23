package com.mohiva.play.silhouette.impl.util

import com.mohiva.play.silhouette.api.util.PasswordHasher

/**
 * Created by rpatel on 8/22/15.
 */
class PBKDF2PasswordHasher(logRounds: Int = 10, iterations: Int = 100000, lenthInBytes: Int = 512)
  extends PasswordHasher{

  import javax.crypto.SecretKeyFactory

  import com.mohiva.play.silhouette.api.util.PasswordInfo

  /**
   * Hashes a password.
   *
   * This implementation does not return the salt separately because it is embedded in the hashed password.
   * Other implementations might need to return it so it gets saved in the backing store.
   *
   * @param plainPassword The password to hash.
   * @return A PasswordInfo containing the hashed password.
   */
  override def hash(plainPassword: String) : PasswordInfo = {
    import org.mindrot.jbcrypt.BCrypt
    val salt =  BCrypt.gensalt(10);
    val hash = getHash(plainPassword, salt, iterations, lenthInBytes);
    return PasswordInfo(id, hash, Some(salt), Some(iterations), Some(lenthInBytes) )
  }

  private def getHash(plainPassword: String, salt: String, iterations: Int, lenthInBytes : Int): String = {
    import javax.crypto.spec.PBEKeySpec
    import org.apache.commons.codec.binary.Base64
    import java.security.spec.InvalidKeySpecException

    val spec: PBEKeySpec = new PBEKeySpec(plainPassword.toCharArray, salt.getBytes, iterations, lenthInBytes)
    var bytes: Array[Byte] = null
    try {
      bytes = keyFactory.generateSecret(spec).getEncoded
      val hash = Base64.encodeBase64String(bytes)
      return hash
    }
    catch {
      case e: InvalidKeySpecException => {
        throw new RuntimeException(e)
      }
    }

  }
  /**
   * Gets the ID of the hasher.
   *
   * @return The ID of the hasher.
   */
  override def id = PBKDF2PasswordHasher.ID
  def keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
  /**
   * Checks if a password matches the hashed version.
   *
   * @param passwordInfo The password retrieved from the backing store.
   * @param suppliedPassword The password supplied by the user trying to log in.
   * @return True if the password matches, false otherwise.
   */
  override def matches(passwordInfo: PasswordInfo, suppliedPassword: String) : Boolean = {
    try {
      val hash = getHash(suppliedPassword, passwordInfo.salt.get, passwordInfo.iterations.get, passwordInfo.lengthInByte.get)
      return hash == passwordInfo.password
    } catch {
      case e => {
        import play.Logger
        Logger.error("Password did not match.", e)
        return false;
      }
    }
  }
}

/**
 * The companion object.
 */
object PBKDF2PasswordHasher {

  /**
   * The ID of the hasher.
   */
  val ID = "PBKDF2"
}

