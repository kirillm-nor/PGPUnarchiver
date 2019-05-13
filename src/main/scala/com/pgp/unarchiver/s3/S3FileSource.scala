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

class S3FileSource(bucketName: String, checkSumPath: String)(
    implicit materialiser: ActorMaterializer,
    system: ActorSystem) {

  import system.dispatcher

  private[this] val supportedExtension = Seq("tar.gz", "gz", "zip")

  private[this] lazy val files: Future[Seq[FileMeta]] = S3
    .listBucket(bucketName, None)
    .flatMapConcat(
      l =>
        S3.getObjectMetadata(bucketName, l.key)
          .filter(_.isDefined)
          .map(o => (l, o.get)))
    .map {
      case (l, o) =>
        val shortExt = l.key.split(".").takeRight(2)
        val ext =
          if (shortExt.mkString(".") == "tar.gz") "tar.gz" else shortExt.last
        FileMeta(
          l.size,
          l.key.split("/").last,
          ext,
          l.key,
          o.headers.asScala.find(h => h.name() == checkSumPath).map(_.value()))
    }
    .filter(f => supportedExtension.contains(f.ext))
    .runWith(Sink.seq)

  private[this] def fileCheckSum(file: FileMeta): Future[String] =
    Source
      .fromFutureSource(
        S3.download(bucketName, file.key)
          .runWith(Sink.head)
          .collect {
            case Some((data, _)) => data
          })
      .runWith(CheckSumShape.sink)

  def filesCheckSums: Future[Seq[(String, String)]] =
    files.flatMap(p =>
      Future.sequence(p.map(l => fileCheckSum(l).map(s => l.name -> s))))

  def filesMeta: Future[Seq[FileMeta]] = files

}
