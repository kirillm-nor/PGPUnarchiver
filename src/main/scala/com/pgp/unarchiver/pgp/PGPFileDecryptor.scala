package com.pgp.unarchiver.pgp

import com.pgp.unarchiver.s3.S3FileSource.FileMeta
import org.bouncycastle.openpgp.PGPUtil

class PGPFileDecryptor(file: FileMeta, PGPLocalPrivateKey: PGPLocalPrivateKey) {

  private[this] def decodeFile = ???

}
