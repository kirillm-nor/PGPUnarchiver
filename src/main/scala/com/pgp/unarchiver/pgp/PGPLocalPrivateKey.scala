package com.pgp.unarchiver.pgp

import java.io.{BufferedInputStream, File, FileInputStream}
import java.security.Security

import org.bouncycastle.openpgp.{PGPException, PGPSecretKey, PGPSecretKeyRing, PGPUtil}
import org.bouncycastle.openpgp.operator.jcajce.{JcaKeyFingerprintCalculator, JcaPGPDigestCalculatorProviderBuilder, JcePBESecretKeyDecryptorBuilder}

class PGPLocalPrivateKey(secretKey: File) {
  private[this] lazy val pgpPrivateKey: PGPSecretKey = new PGPSecretKeyRing(PGPUtil.getDecoderStream(new BufferedInputStream(new FileInputStream(secretKey))),
    new JcaKeyFingerprintCalculator()).getSecretKey

  private[this] def getPrivateKey(passPhrase: Array[Char]) =
    try {
      val provider = Security.getProvider("BC")
      val decryptorFactory = new JcePBESecretKeyDecryptorBuilder(new JcaPGPDigestCalculatorProviderBuilder()
        .setProvider(provider).build()).setProvider(provider).build(passPhrase)
      pgpPrivateKey.extractPrivateKey(decryptorFactory)
    }
    catch {
      case e: PGPException if e.getMessage.contains("checksum mismatch") => throw new IllegalArgumentException("Incorrect config", e)
    }
}

object PGPLocalPrivateKey {
  def apply(secretKey: File): PGPLocalPrivateKey = new PGPLocalPrivateKey(secretKey)
}
