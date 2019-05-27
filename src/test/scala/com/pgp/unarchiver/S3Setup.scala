package com.pgp.unarchiver

import java.io.{File, OutputStream}

import awscala.s3.{Bucket, S3}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait S3Setup {
  implicit def s3Client: S3

  def uploadFile(name: String,
                 bucketName: String,
                 file: File): Future[String] = {
    Future {
      s3Client.put(Bucket(bucketName), name, file)
    }.map(_.key)
  }

}
