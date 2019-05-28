package com.pgp.unarchiver

import java.io.InputStream
import java.nio.file.Path

import akka.Done
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import awscala.s3.{Bucket, S3}
import com.pgp.unarchiver.pgp.{
  PGPFileDecryptUnarchiveSource,
  PGPLocalPrivateKey,
  PGPSourceShape
}
import com.pgp.unarchiver.s3.S3FileSource
import com.pgp.unarchiver.shape.LineStringProcessingShape

import scala.concurrent.Future

/**
  * Pipe to process pgp file with textual content.
  *
  * @param bucketName
  * @param pgpKeyPath
  * @param passPhrase
  * @param materialiser
  * @param system
  * @param s3Client
  */
class PgpUnarchiverPipe(bucketName: String,
                        pgpKeyPath: Path,
                        passPhrase: String,
                        prefix: Option[String] = None)(
    implicit materialiser: ActorMaterializer,
    logger: LoggingAdapter,
    system: ActorSystem,
    s3Client: S3) {

  import system.dispatcher

  implicit private[this] val bucket = Bucket(bucketName)

  private[this] val s3FileSource =
    new S3FileSource(bucketName, "checksum", prefix)
  private[this] val privateKey = new PGPLocalPrivateKey(pgpKeyPath.toFile)

  /**
    *
    */
  def compareCheckSum: Future[Boolean] = {
    val filesF = s3FileSource.filesMeta
    val checkF = s3FileSource.filesCheckSums
    for {
      files <- filesF
      check <- checkF
    } yield {
      val greenMap = files.map(m => m.name -> m.checkSum.getOrElse("")).toMap
      val checkMap = check.toMap
      val inters = greenMap.keySet
        .intersect(checkMap.keySet)
      val count = inters
        .foldLeft(0) {
          case (i, k) if greenMap(k) == checkMap(k) => i + 1
          case (i, _)                               => i
        }
      logger.debug("Files checksums calculated")
      count == greenMap.size
    }
  }

  /**
    *
    */
  def decryptionFlow: Future[Seq[Done]] = {
    s3FileSource.filesMeta.flatMap(s =>
      Future.traverse(s) { f =>
        PGPSourceShape(bucketName, f.key, f.size)
          .flatMapConcat { _ =>
            val is = s3Client
              .get(bucket, f.key)
              .map(_.content)
              .getOrElse(new InputStream {
                override def read(): Int = -1
              })
            PGPFileDecryptUnarchiveSource(is,
                                          f.ext,
                                          privateKey,
                                          passPhrase.toCharArray)
          }
          .viaMat(LineStringProcessingShape.apply)(Keep.right)
          .runForeach(s => logger.debug(s))
          .recover {
            case ex =>
              logger.error("Exceptional pipe execution", ex)
              Done
          }
    })
  }
}

object PgpUnarchiverPipe {
  def apply(bucketName: String,
            pgpKeyPath: Path,
            passPhrase: String,
            prefix: Option[String] = None)(
      implicit materialiser: ActorMaterializer,
      loggingAdapter: LoggingAdapter,
      system: ActorSystem,
      s3Client: S3): PgpUnarchiverPipe =
    new PgpUnarchiverPipe(bucketName, pgpKeyPath, passPhrase, prefix)
}
