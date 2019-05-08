package com.pgp.unarchiver.pgp

import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory

object PGPHelper {
  implicit class JcaPGPObjectFactoryWithEncryptedData(f: JcaPGPObjectFactory) {
    def encryptedDataList: PGPEncryptedDataList = {
      f.nextObject match {
        case e: PGPEncryptedDataList => e
        case _ => f.encryptedDataList
      }
    }
  }
}
