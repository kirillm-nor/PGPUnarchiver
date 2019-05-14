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

sealed trait Extention {
  def name: String
}

case object TAR_GZ extends Extention {
  override def name: String = "tar.gz"
}

case object GZ extends Extention {
  override def name: String = "gz"
}

case object ZIP extends Extention {
  override def name: String = "zip"
}

case object UNSUPPORTED extends Extention {
  override def name: String = "xxx"
}

object Extention {
  def apply(name: String): Extention = {
    name.split(".").takeRight(3).toSeq match {
      case Seq("tar", "gz", _) => TAR_GZ
      case Seq(_, "gz", _)     => GZ
      case Seq(_, "zip", _)    => ZIP
      case _                   => UNSUPPORTED
    }

  }
}

object S3FileSource {

  case class FileMeta(size: Long,
                      name: String,
                      ext: Extention,
                      key: String,
                      checkSum: Option[String])

}

class S3FileSource(bucketName: String, checkSumPath: String)(
    implicit materialiser: ActorMaterializer,
    system: ActorSystem) {

  import system.dispatcher

  private[this] lazy val files: Future[Seq[FileMeta]] = S3
    .listBucket(bucketName, None)
    .flatMapConcat(
      l =>
        S3.getObjectMetadata(bucketName, l.key)
          .filter(_.isDefined)
          .map(o => (l, o.get)))
    .map {
      case (l, o) =>
        FileMeta(
          l.size,
          l.key.split("/").last,
          Extention(l.key),
          l.key,
          o.headers.asScala.find(h => h.name() == checkSumPath).map(_.value()))
    }
    .filterNot(f => f.ext == UNSUPPORTED)
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
