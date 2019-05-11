package com.pgp.unarchiver.pgp

import java.io.{BufferedInputStream, InputStream}
import java.security.Security

import akka.Done
import akka.stream.stage._
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.util.ByteString
import awscala.s3._
import com.pgp.unarchiver.archive.{UnarchiveAction, UnarchiveEventAction}
import com.pgp.unarchiver.pgp.Extractor._
import com.pgp.unarchiver.pgp.PGPFileDecryptUnarchiveSource.{
  IntegrityException,
  NotEncryptedMessageException
}
import com.pgp.unarchiver.pgp.PGPHelper._
import com.pgp.unarchiver.s3.S3FileSource.FileMeta
import org.bouncycastle.openpgp._
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder

import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag

object PGPFileDecryptUnarchiveSource {

  case object NotEncryptedMessageException extends Exception
  case object IntegrityException extends Exception

  def apply[E <: UnarchiveEventAction](file: FileMeta,
                                       pgpLocalPrivateKey: PGPLocalPrivateKey,
                                       passPhrase: Array[Char])(
      implicit s3Client: S3,
      bucket: Bucket,
      unarchiver: UnarchiveAction[E#UnarchiveDTO])
    : PGPFileDecryptUnarchiveSource[E] =
    new PGPFileDecryptUnarchiveSource[E](file, pgpLocalPrivateKey, passPhrase)
}

class PGPFileDecryptUnarchiveSource[E <: UnarchiveEventAction](
    file: FileMeta,
    pgpLocalPrivateKey: PGPLocalPrivateKey,
    passPhrase: Array[Char])(implicit s3Client: S3,
                             bucket: Bucket,
                             unarchiver: UnarchiveAction[E#UnarchiveDTO])
    extends GraphStageWithMaterializedValue[SourceShape[ByteString],
                                            Future[Done]] {

  val out: Outlet[ByteString] = Outlet[ByteString]("PGPFileDecryptor.out")

  override val shape: SourceShape[ByteString] = SourceShape.of(out)

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes): (GraphStageLogic, Future[Done]) = {
    val p: Promise[Done] = Promise()
    (new GraphStageLogic(shape) with StageLogging with OutHandler {

      private[this] lazy val requestedFileStream = s3Client
        .get(bucket, file.key)
        .map(_.content)
        .getOrElse(new InputStream {
          override def read(): Int = -1
        })

      var strm: InputStream = null
      var data: PGPPublicKeyEncryptedData = null

      private[this] def decryptFile(): Unit = {
        val verifiedStream = PGPUtil.getDecoderStream(requestedFileStream)
        try {
          val (key, data) = extractPrivateKey(passPhrase, verifiedStream)
          val decryptedStream = getDecryptedStream(key, data)
          val plainFactory = new JcaPGPObjectFactory(decryptedStream)

          val msg = plainFactory.nextObject match {
            case msg: PGPLiteralData      => extract[PGPLiteralData](msg)
            case cData: PGPCompressedData => extract[PGPCompressedData](cData)
            case msg: PGPOnePassSignature => extract[PGPOnePassSignature](msg)
            case _                        => throw NotEncryptedMessageException
          }

          this.strm = unarchiver.wrapStream(msg.getInputStream).stream
          this.data = data
        }
      }

      override def preStart(): Unit = decryptFile()

      override def onDownstreamFinish(): Unit = {
        if (data.isIntegrityProtected && !data.verify()) p.success(Done)
        else p.failure(IntegrityException)
      }

      override def onPull(): Unit = {
        val bis = new BufferedInputStream(strm)
        val len = 8 * 1024
        val ba = new Array[Byte](len)
        bis.read(ba, 0, len) match {
          case -1 => completeStage()
          case _  => push(out, ByteString.fromArray(ba, 0, len))
        }
      }
    }, p.future)
  }

  private[this] def extract[T: ClassTag](t: Any)(
      implicit e: PGPLiteralExtractor[T]) =
    e.extract(t)

  private def getDecryptedStream(key: PGPPrivateKey,
                                 data: PGPPublicKeyEncryptedData) = {
    val provider = Security.getProvider("BC")
    val dataDecryptorFactory = new JcePublicKeyDataDecryptorFactoryBuilder()
      .setProvider(provider)
      .setContentProvider(provider)
      .build(key)
    data.getDataStream(dataDecryptorFactory)
  }

  private def extractPrivateKey(passPhrase: Array[Char],
                                verifiedStream: InputStream) = {
    import collection.JavaConverters._

    val pgpObjFactory = new JcaPGPObjectFactory(verifiedStream)
    val encryptedDataList = pgpObjFactory.encryptedDataList
    val it = encryptedDataList.getEncryptedDataObjects.asScala
    it.collect {
        case p: PGPPublicKeyEncryptedData =>
          pgpLocalPrivateKey.getPrivateKey(p.getKeyID, passPhrase).map(_ -> p)
      }
      .collect {
        case Some(k) => k
      }
      .toSeq
      .headOption
      .fold {
        throw new IllegalArgumentException("Secret key for message not found.")
      }(identity)
  }

}
