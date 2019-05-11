package com.pgp.unarchiver.pgp

import java.io.BufferedInputStream

import com.pgp.unarchiver.pgp.PGPFileDecryptUnarchiveSource.NotEncryptedMessageException
import org.bouncycastle.openpgp.{
  PGPCompressedData,
  PGPLiteralData,
  PGPOnePassSignature
}
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory

import scala.reflect.ClassTag

trait PGPLiteralExtractor[A] {
  def extract(t: Any)(implicit tag: ClassTag[A]): PGPLiteralData
}

object Extractor {
  implicit val literalDataExtractor: PGPLiteralExtractor[PGPLiteralData] =
    new PGPLiteralExtractor[PGPLiteralData] {
      override def extract(t: Any)(
          implicit tag: ClassTag[PGPLiteralData]): PGPLiteralData =
        t.asInstanceOf[PGPLiteralData]
    }

  implicit val compressedDataExtractor: PGPLiteralExtractor[PGPCompressedData] =
    new PGPLiteralExtractor[PGPCompressedData] {
      override def extract(t: Any)(
          implicit tag: ClassTag[PGPCompressedData]): PGPLiteralData = {
        def extractInternal(t: Any): PGPLiteralData = t match {
          case msg: PGPLiteralData => msg
          case cData: PGPCompressedData =>
            val compressedStream = new BufferedInputStream(cData.getDataStream)
            val pgpFact = new JcaPGPObjectFactory(compressedStream)
            extractInternal(pgpFact.nextObject)
          case _: PGPOnePassSignature => throw NotEncryptedMessageException
          case _                      => throw NotEncryptedMessageException
        }
        extractInternal(t)
      }
    }

  implicit val onePassDataExtractor: PGPLiteralExtractor[PGPOnePassSignature] =
    new PGPLiteralExtractor[PGPOnePassSignature] {
      override def extract(t: Any)(
          implicit tag: ClassTag[PGPOnePassSignature]): PGPLiteralData =
        throw NotEncryptedMessageException
    }
}
