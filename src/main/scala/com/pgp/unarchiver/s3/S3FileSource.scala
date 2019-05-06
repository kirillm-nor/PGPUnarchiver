package com.pgp.unarchiver.s3

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.ByteRange
import akka.stream.ActorMaterializer
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.pgp.unarchiver.s3.S3FileSource.FileMeta
import com.pgp.unarchiver.shape.CheckSumShape

import scala.collection.parallel.ParSeq
import scala.concurrent.Future
import scala.collection.JavaConverters._

object S3FileSource {

  case class FileMeta(size: Long,
                      name: String,
                      ext: String,
                      key: String,
                      checkSum: Option[String])

}

class S3FileSource(bucketName: String, region: String)(
    implicit materialiser: ActorMaterializer,
    system: ActorSystem) {

  import system.dispatcher

  private lazy val files: Future[ParSeq[FileMeta]] = S3
    .listBucket(bucketName, None)
    .flatMapConcat(
      l =>
        S3.getObjectMetadata(bucketName, l.key)
          .filter(_.isDefined)
          .map(o => (l, o.get)))
    .map {
      case (l, o) =>
        FileMeta(l.size,
                 l.key.split("/").last,
                 l.key.split(".").last,
                 l.key,
                 o.headers.asScala.find(h => h.name() == "md5").map(_.value()))
    }
    .runWith(Sink.seq)
    .map(_.par)

  private[this] def fileCheckSum(file: FileMeta): Future[String] =
    Source
      .fromFutureSource(
        S3.download(bucketName, file.key)
          .runWith(Sink.head)
          .collect {
            case Some((data, _)) => data
          })
      .runWith(CheckSumShape.sink)

  def filesCheckSums: Future[ParSeq[String]] =
    files.flatMap(p => Future.sequence(p.map(fileCheckSum)))

}
