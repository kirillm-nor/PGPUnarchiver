package com.pgp.unarchiver

import java.io.{BufferedOutputStream, File, FileOutputStream, OutputStream}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

trait FileSetup {

  private[this] def generateTxtFileContent(size: Long): Stream[String] =
    if (size == 0) Stream.empty
    else textLine #:: generateTxtFileContent(size - 1)

  private[this] def textLine =
    Random.alphanumeric.take(120).foldLeft("\nNew Line ")(_ + _)

  private[this] def openFileOutputStreamWithContent: (String, OutputStream) = {
    val file = File.createTempFile("test-decryption", ".txt")
    println(s"File created ${file.getAbsolutePath}")
    (file.getAbsolutePath, new BufferedOutputStream(new FileOutputStream(file),
      10 * 1024 * 1024))
  }

  private[this] def createStreamWithLines(lines: Int)(key: String, os: OutputStream): Future[String] =
    Future {
      generateTxtFileContent(lines).foreach(s => os.write(s.getBytes))
    }
      .map(_ => key)
      .andThen {
        case _ =>
          os.flush()
          os.close()
      }

  def fileWithLines(lines: Int): Future[String] = (createStreamWithLines(lines) _).tupled(openFileOutputStreamWithContent)
}
