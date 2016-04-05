package com.mohiva.play.silhouette.password

import com.mohiva.play.silhouette.api.util.PasswordInfo
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class PasswordInfoSpec extends Specification with Mockito {

  "The `hasher` val" should {
    "normally equal the constructor parameter" in {
      PasswordInfo("hasher", "password").hasher must be equalTo "hasher"
    }

    "fix itself when given simply as 'bcrypt'" in {
      val hasher = new BCryptPasswordHasher(6)
      val correctPasswordInfo = hasher.hash("password")
      val legacyPasswordInfo = PasswordInfo(BCryptPasswordHasher.ID, correctPasswordInfo.password)
      legacyPasswordInfo.hasher must not be equalTo(BCryptPasswordHasher.ID)
      correctPasswordInfo must be equalTo legacyPasswordInfo
    }
  }
}
