package com.pgp.unarchiver.pgp

import java.io.{BufferedInputStream, InputStream}
import java.security.Security

import akka.Done
import akka.stream.scaladsl.Source
import akka.stream.stage._
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.util.ByteString
import awscala.s3._
import com.pgp.unarchiver.archive.EventAction.GZipUnarchiveEventAction.GZipUnarchiveEventAction
import com.pgp.unarchiver.archive.EventAction.TarGZipUnarchiveEventAction.TarGZipUnarchiveEventAction
import com.pgp.unarchiver.archive.EventAction.ZipUnarchiveEventAction.ZipUnarchiveEventAction
import com.pgp.unarchiver.archive.{
  StreamSeeker,
  UnarchiveAction,
  UnarchiveEventAction
}
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
import com.pgp.unarchiver.archive.Unarchiver._
import com.pgp.unarchiver.s3.{Extention, GZ, TAR_GZ, ZIP}
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.utils.IOUtils

/**
  *
  */
object PGPFileDecryptUnarchiveSource {

  case object NotEncryptedMessageException extends Exception

  case object IntegrityException extends Exception

  def apply(is: InputStream,
            fileExt: Extention,
            pgpLocalPrivateKey: PGPLocalPrivateKey,
            passPhrase: Array[Char])
    : Source[ByteString, Future[(Long, StreamSeeker)]] = fileExt match {
    case TAR_GZ =>
      Source.fromGraph(
        new PGPFileDecryptUnarchiveSource[TarGZipUnarchiveEventAction](
          is,
          pgpLocalPrivateKey,
          passPhrase))
    case GZ =>
      Source.fromGraph(
        new PGPFileDecryptUnarchiveSource[GZipUnarchiveEventAction](
          is,
          pgpLocalPrivateKey,
          passPhrase))
    case ZIP =>
      Source.fromGraph(
        new PGPFileDecryptUnarchiveSource[ZipUnarchiveEventAction](
          is,
          pgpLocalPrivateKey,
          passPhrase))
  }
}

/**
  * Stream file source for S3 hosted files which wraps file input stream to stream of ByteString.
  * Also decrypt and unarchive incoming stream.
  *
  * @param fileInputStream S3 file data input stream
  * @param pgpLocalPrivateKey
  * @param passPhrase
  * @param unarchiver
  * @tparam E
  */
class PGPFileDecryptUnarchiveSource[E <: UnarchiveEventAction](
    fileInputStream: InputStream,
    pgpLocalPrivateKey: PGPLocalPrivateKey,
    passPhrase: Array[Char])(implicit
                             unarchiver: UnarchiveAction[E#UnarchiveDTO])
    extends GraphStageWithMaterializedValue[SourceShape[ByteString],
                                            Future[(Long, StreamSeeker)]] {

  val out: Outlet[ByteString] =
    Outlet[ByteString]("PGPFileDecryptUnarchiveSource.out")

  override val shape: SourceShape[ByteString] = SourceShape.of(out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes)
    : (GraphStageLogic, Future[(Long, StreamSeeker)]) = {
    val p: Promise[(Long, StreamSeeker)] = Promise()
    (new GraphStageLogic(shape) with StageLogging with OutHandler {

      var strm: StreamSeeker = null
      var data: PGPPublicKeyEncryptedData = null
      var counter: Long = 0l

      private[this] def decryptFile(): Unit = {
        val verifiedStream = PGPUtil.getDecoderStream(fileInputStream)
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
        if (data.isIntegrityProtected && !data.verify())
          p.success((counter, strm))
        else p.failure(IntegrityException)
        IOUtils.closeQuietly(strm.asInstanceOf[InputStream])
      }

      override def onPull(): Unit = {
        val len = 8 * 1024
        val ba = new Array[Byte](len)
        strm.seek(ba, 0, ba.length) match {
          case None =>
            p.success((counter, strm))
            complete(out)
          case Some(_) =>
            counter = counter + 1
            push(out, ByteString.fromArray(ba, 0, len))
        }
      }

      setHandler(out, this)
    }, p.future)
  }

  private[this] def extract[T: ClassTag](t: Any)(
      implicit e: PGPLiteralExtractor[T]) =
    e.extract(t)

  private[this] def getDecryptedStream(key: PGPPrivateKey,
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
