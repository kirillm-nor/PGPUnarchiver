package com.pgp.unarchiver

import java.nio.file.Path

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.pgp.unarchiver.pgp.PGPLocalPrivateKey
import com.pgp.unarchiver.s3.S3FileSource

class PgpUnarchiverPipe(bucketName: String, pgpKeyPath: Path)(
    implicit materialiser: ActorMaterializer,
    system: ActorSystem) {

  private[this] val s3FileSource = new S3FileSource(bucketName, "md5")
  private[this] val privateKey = new PGPLocalPrivateKey(pgpKeyPath.toFile)

  def compareCheckSum = {
    for {
      files <- s3FileSource.filesMeta
      check <- s3FileSource.filesCheckSums
    } yield {
      val greenMap = files.map(m => m.name -> m.checkSum.getOrElse("")).toMap
    }
  }
}
