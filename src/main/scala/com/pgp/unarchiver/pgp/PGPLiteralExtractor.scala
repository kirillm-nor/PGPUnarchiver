package com.pgp.unarchiver.pgp

import org.bouncycastle.openpgp.PGPLiteralData

trait PGPLiteralExtractor[+A] {
  def extract: PGPLiteralData
}
