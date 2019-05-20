package com.pgp.unarchiver.pgp

import java.io.{BufferedInputStream, File, FileInputStream}
import java.security.Security

import org.bouncycastle.openpgp.{PGPException, PGPPrivateKey, PGPSecretKey, PGPSecretKeyRing, PGPSecretKeyRingCollection, PGPUtil}
import org.bouncycastle.openpgp.operator.jcajce.{JcaKeyFingerprintCalculator, JcaPGPDigestCalculatorProviderBuilder, JcePBESecretKeyDecryptorBuilder}

/**
  *
  * @param secretKey
  */
class PGPLocalPrivateKey(secretKey: File) {
  private[this] lazy val pgpPrivateKeyRingCollection: PGPSecretKeyRingCollection = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(new BufferedInputStream(new FileInputStream(secretKey))),
    new JcaKeyFingerprintCalculator())

  def getPrivateKey(keyId: Long, passPhrase: Array[Char]): Option[PGPPrivateKey] =
    try {
      val provider = Security.getProvider("BC")
      val decryptorFactory = new JcePBESecretKeyDecryptorBuilder(new JcaPGPDigestCalculatorProviderBuilder()
        .setProvider(provider).build()).setProvider(provider).build(passPhrase)
      Option(pgpPrivateKeyRingCollection.getSecretKey(keyId)).map(_.extractPrivateKey(decryptorFactory))
    }
    catch {
      case e: PGPException if e.getMessage.contains("checksum mismatch") => throw new IllegalArgumentException("Incorrect config", e)
    }
}

object PGPLocalPrivateKey {
  def apply(secretKey: File): PGPLocalPrivateKey = new PGPLocalPrivateKey(secretKey)
}
