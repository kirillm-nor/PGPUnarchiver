package com.pgp.unarchiver.pgp

import java.io.{BufferedInputStream, InputStream}
import java.security.Security

import com.pgp.unarchiver.s3.S3FileSource.FileMeta
import org.bouncycastle.openpgp.{PGPCompressedData, PGPEncryptedDataList, PGPLiteralData, PGPOnePassSignature, PGPPrivateKey, PGPPublicKeyEncryptedData, PGPUtil}
import awscala._
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder
import s3._
import PGPHelper._
import com.pgp.unarchiver.pgp.PGPFileDecryptor.{IntegrityException, NotEncryptedMessageException}

object PGPFileDecryptor {

  case object NotEncryptedMessageException extends Exception

  case object IntegrityException extends Exception

  def apply(file: FileMeta, pgpLocalPrivateKey: PGPLocalPrivateKey)(implicit s3Client: S3, bucket: Bucket): PGPFileDecryptor =
    new PGPFileDecryptor(file, pgpLocalPrivateKey)
}

class PGPFileDecryptor(file: FileMeta, pgpLocalPrivateKey: PGPLocalPrivateKey)(implicit s3Client: S3, bucket: Bucket) {

  private[this] lazy val requestedFileStream = s3Client.get(bucket, file.key).map(_.content).getOrElse(InputStream.nullInputStream())

  private[this] def decryptFile(passPhrase: Array[Char]) = {
    val verifiedStream = PGPUtil.getDecoderStream(requestedFileStream)
    try {
      val (key, data) = extractPrivateKey(passPhrase, verifiedStream)
      val decryptedStream = getDecryptedStream(key, data)
      val plainFactory = new JcaPGPObjectFactory(decryptedStream)

      def extractLiteral(packet: Any): PGPLiteralData = packet match {
        case msg: PGPLiteralData => msg
        case cData: PGPCompressedData =>
          val compressedStream = new BufferedInputStream(cData.getDataStream)
          val pgpFact = new JcaPGPObjectFactory(compressedStream)
          extractLiteral(pgpFact.nextObject)
        case msg: PGPOnePassSignature => throw NotEncryptedMessageException
        case _ => throw NotEncryptedMessageException
      }

      val msg = extractLiteral(plainFactory.nextObject)
      msg.getInputStream
      if (data.isIntegrityProtected && !data.verify()) throw IntegrityException
    }
  }

  private def getDecryptedStream(key: PGPPrivateKey, data: PGPPublicKeyEncryptedData) = {
    val provider = Security.getProvider("BC")
    val dataDecryptorFactory = new JcePublicKeyDataDecryptorFactoryBuilder().setProvider(provider).setContentProvider(provider).build(key)
    data.getDataStream(dataDecryptorFactory)
  }

  private def extractPrivateKey(passPhrase: Array[Char], verifiedStream: InputStream) = {
    import collection.JavaConverters._

    val pgpObjFactory = new JcaPGPObjectFactory(verifiedStream)
    val encryptedDataList = pgpObjFactory.encryptedDataList
    val it = encryptedDataList.getEncryptedDataObjects.asScala
    it.collect {
      case p: PGPPublicKeyEncryptedData => pgpLocalPrivateKey.getPrivateKey(p.getKeyID, passPhrase).map(_ -> p)
    }.collect {
      case Some(k) => k
    }.toSeq.headOption.fold {
      throw new IllegalArgumentException("Secret key for message not found.")
    }(identity)
  }
}
