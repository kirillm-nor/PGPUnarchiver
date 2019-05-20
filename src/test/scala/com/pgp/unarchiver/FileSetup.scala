package com.pgp.unarchiver

import java.io.{BufferedOutputStream, ByteArrayOutputStream, OutputStream}

import scala.util.Random

trait FileSetup {

  protected def generateTxtFileContent(size: Long): Stream[String] =
    textLine #:: generateTxtFileContent(size - 1)

  private[this] def textLine =
    Random.alphanumeric.take(120).foldLeft("\nNew Line")(_ + _)

  def openFileOutputStreamWithContent: OutputStream =
    new BufferedOutputStream(new ByteArrayOutputStream(64 * 1024),
                             10 * 1024 * 1024)
}
