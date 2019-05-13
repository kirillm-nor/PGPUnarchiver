package com.pgp.unarchiver

import java.nio.file.Path

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.ByteString
import awscala.s3.{Bucket, S3}
import com.pgp.unarchiver.pgp.{PGPFileDecryptUnarchiveSource, PGPLocalPrivateKey, PGPSourceShape}
import com.pgp.unarchiver.s3.S3FileSource
import com.pgp.unarchiver.shape.ByteStringProcessShape

import scala.concurrent.Future

/**
  *
  * @param bucketName
  * @param pgpKeyPath
  * @param passPhrase
  * @param materialiser
  * @param system
  * @param s3Client
  */
class PgpUnarchiverPipe(bucketName: String, pgpKeyPath: Path, passPhrase: String)(
  implicit materialiser: ActorMaterializer,
  system: ActorSystem, s3Client: S3) {

  import system.dispatcher

  implicit private val bucket = Bucket(bucketName)

  private[this] val s3FileSource = new S3FileSource(bucketName, "md5")
  private[this] val privateKey = new PGPLocalPrivateKey(pgpKeyPath.toFile)

  /**
    *
    */
  lazy val compareCheckSum: Future[Boolean] = {
    for {
      files <- s3FileSource.filesMeta
      check <- s3FileSource.filesCheckSums
    } yield {
      val greenMap = files.map(m => m.name -> m.checkSum.getOrElse("")).toMap
      val checkMap = check.toMap
      val count = greenMap.keySet
        .intersect(checkMap.keySet)
        .map(k => greenMap(k) == checkMap(k)).foldLeft(0) {
        case (i, true) => i + 1
        case (i, false) => i
      }
      count == greenMap.size
    }
  }

  /**
    *
    */
  lazy val decryptionFlow: Future[Seq[Done]] = {
    s3FileSource.filesMeta.flatMap(s => Future.traverse(s) { f =>
      PGPSourceShape(bucketName, f.key, f.size)
        .flatMapConcat(_ =>
          PGPFileDecryptUnarchiveSource(f, new PGPLocalPrivateKey(pgpKeyPath.toFile), passPhrase.toCharArray))
        .via(Flow.fromGraph(new ByteStringProcessShape[ByteString]({ bs =>
          println(bs.toString())
          bs
        })))
        .runWith(Sink.ignore).recover {
        case ex => Done
      }
    })
  }
}
