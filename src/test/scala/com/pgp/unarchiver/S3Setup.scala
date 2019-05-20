package com.pgp.unarchiver

import java.io.OutputStream

import awscala.s3.S3
import com.amazonaws.services.s3.model.UploadPartRequest

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait S3Setup {
  implicit def s3Client: S3

  def uploadBigFile(name: String,
                    bucketName: String,
                    outputStream: OutputStream): Future[String] = {
    val uploadRequest = new UploadPartRequest()
    Future {
      s3Client.uploadPart(uploadRequest)
    }.map(_.getETag)
  }

}
