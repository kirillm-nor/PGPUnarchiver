package com.pgp.unarchiver.pgp

import java.io.{BufferedInputStream, File, FileInputStream}
import java.security.Security

import org.bouncycastle.openpgp.{PGPException, PGPPrivateKey, PGPSecretKey, PGPSecretKeyRing, PGPUtil}
import org.bouncycastle.openpgp.operator.jcajce.{JcaKeyFingerprintCalculator, JcaPGPDigestCalculatorProviderBuilder, JcePBESecretKeyDecryptorBuilder}

/**
  *
  * @param secretKey
  */
class PGPLocalPrivateKey(secretKey: File) {
  private[this] lazy val pgpPrivateKeyRing: PGPSecretKeyRing = new PGPSecretKeyRing(PGPUtil.getDecoderStream(new BufferedInputStream(new FileInputStream(secretKey))),
    new JcaKeyFingerprintCalculator())

  def getPrivateKey(keyId: Long, passPhrase: Array[Char]): Option[PGPPrivateKey] =
    try {
      val provider = Security.getProvider("BC")
      val decryptorFactory = new JcePBESecretKeyDecryptorBuilder(new JcaPGPDigestCalculatorProviderBuilder()
        .setProvider(provider).build()).setProvider(provider).build(passPhrase)
      Option(pgpPrivateKeyRing.getSecretKey(keyId)).map(_.extractPrivateKey(decryptorFactory))
    }
    catch {
      case e: PGPException if e.getMessage.contains("checksum mismatch") => throw new IllegalArgumentException("Incorrect config", e)
    }
}

object PGPLocalPrivateKey {
  def apply(secretKey: File): PGPLocalPrivateKey = new PGPLocalPrivateKey(secretKey)
}
