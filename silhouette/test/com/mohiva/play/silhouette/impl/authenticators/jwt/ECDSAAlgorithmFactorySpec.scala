package com.mohiva.play.silhouette.impl.authenticators.jwt

import com.nimbusds.jose.{ JWSAlgorithm, JWSSigner, JWSVerifier }
import org.specs2.control.NoLanguageFeatures
import org.specs2.matcher.JsonMatchers
import org.specs2.mock.Mockito
import play.api.test.PlaySpecification

class ECDSAAlgorithmFactorySpec extends PlaySpecification with Mockito with JsonMatchers with NoLanguageFeatures {

  "ECDSAAlgorithmFactorySpec" should {
    "createVerifier" in {
      val verifier: JWSVerifier = new ECDSAAlgorithmFactory(JWSAlgorithm.ES256).createVerifier("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEW1rprhka9kZ8oGB8e90VOFHyltmuJwYyUtcut8EWr9agJaCMH7f+GvTQyhKnMJmEBMReS5FIS/eeTJz3NuiGyQ==")
      verifier.supportedAlgorithms().contains(JWSAlgorithm.ES256) === true

    }
    "createSigner" in {
      val signer: JWSSigner = new ECDSAAlgorithmFactory(JWSAlgorithm.ES256).createSigner("MIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQgHUW9ZGv4tn4h7V6wgDSNPRP4XkE5LO78XMd4isiJQGagCgYIKoZIzj0DAQehRANCAARbWumuGRr2RnygYHx73RU4UfKW2a4nBjJS1y63wRav1qAloIwft/4a9NDKEqcwmYQExF5LkUhL955MnPc26IbJ")
      signer.supportedAlgorithms().contains(JWSAlgorithm.ES256) === true
    }

    "use algorithm instantiated with" in {
      new ECDSAAlgorithmFactory(JWSAlgorithm.ES256).jwsAlgorithm === JWSAlgorithm.ES256
    }
  }
}
