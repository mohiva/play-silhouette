package com.mohiva.play.silhouette.impl.authenticators.jwt

import java.security.interfaces.{ ECPrivateKey, ECPublicKey }
import java.security.spec.X509EncodedKeySpec
import java.security.{ KeyFactory, PrivateKey, PublicKey }

import com.nimbusds.jose.crypto.{ ECDSASigner, ECDSAVerifier, MACSigner, MACVerifier }
import com.nimbusds.jose.{ JWSAlgorithm, JWSSigner, JWSVerifier }
import org.apache.commons.codec.binary.Base64

trait JWTAlgorithmFactory extends JWTAlgorithmVerifierFactory with JWTAlgorithmSignerFactory

trait JWTAlgorithmVerifierFactory {
  def createVerifier(key: String): JWSVerifier
}

trait JWTAlgorithmSignerFactory {
  def createSigner(key: String): JWSSigner
  val jwsAlgorithm: JWSAlgorithm
}

class ECDSAAlgorithmFactory(val jwsAlgorithm: JWSAlgorithm) extends JWTAlgorithmFactory {

  override def createVerifier(key: String): JWSVerifier = {
    val publicKey: PublicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.decodeBase64(key.getBytes("UTF-8"))))
    val eCPublicKey: ECPublicKey = publicKey.asInstanceOf[ECPublicKey]
    new ECDSAVerifier(eCPublicKey.getW.getAffineX, eCPublicKey.getW.getAffineY)
  }

  override def createSigner(key: String): JWSSigner = {
    val privateKey: PrivateKey = KeyFactory.getInstance("EC").generatePrivate(new X509EncodedKeySpec(Base64.decodeBase64(key.getBytes("UTF-8"))))
    val eCPrivateKey: ECPrivateKey = privateKey.asInstanceOf[ECPrivateKey]
    new ECDSASigner(eCPrivateKey.getS)
  }
}

class MACAlgorithmFactory(val jwsAlgorithm: JWSAlgorithm) extends JWTAlgorithmFactory {
  override def createVerifier(key: String): JWSVerifier = new MACVerifier(key)
  override def createSigner(key: String): JWSSigner = new MACSigner(key)
}
