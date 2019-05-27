package com.pgp.unarchiver.archive

import java.io.InputStream
import java.util.zip.ZipInputStream

import org.apache.commons.compress.archivers.tar.{
  TarArchiveEntry,
  TarArchiveInputStream
}
import org.apache.commons.compress.archivers.zip.{
  ZipArchiveEntry,
  ZipArchiveInputStream
}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

/**
  *
  * @tparam D
  */
trait UnarchiveAction[D <: EventDTO] {
  def wrapStream(in: InputStream): D
}

object Unarchiver {
  implicit val zipUnarchiver: UnarchiveAction[ZipEventDTO] =
    new UnarchiveAction[ZipEventDTO] {
      override def wrapStream(in: InputStream): ZipEventDTO =
        ZipEventDTO(new ZipArchiveInputStream(in) with StreamSeeker {
          override def seek(b: Array[Byte], off: Int, len: Int): Option[Int] =
            read(b, off, len) match {
              case -1 => Option(getNextZipEntry).flatMap(_ => seek(b, off, len))
              case x  => Some(x)
            }
        })
    }

  implicit val tarGzipUnarchiver: UnarchiveAction[TarGZipEventDTO] =
    new UnarchiveAction[TarGZipEventDTO] {
      override def wrapStream(in: InputStream): TarGZipEventDTO =
        TarGZipEventDTO(
          new TarArchiveInputStream(new GzipCompressorInputStream(in))
          with StreamSeeker {
            override def seek(b: Array[Byte], off: Int, len: Int): Option[Int] =
              read(b, off, len) match {
                case -1 =>
                  Option(getNextTarEntry).flatMap(_ => seek(b, off, len))
                case x => Some(x)
              }
          })
    }

  implicit val gzipUnarchiver: UnarchiveAction[GZipEventDTO] =
    new UnarchiveAction[GZipEventDTO] {
      override def wrapStream(in: InputStream): GZipEventDTO =
        GZipEventDTO(new GzipCompressorInputStream(in) with StreamSeeker {
          override def seek(b: Array[Byte], off: Int, len: Int): Option[Int] =
            Option(read(b, off, len)).filter(_ != -1)
        })
    }
}
