package com.pgp.unarchiver.archive

import java.io.InputStream
import java.util.zip.ZipInputStream

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

trait UnarchiveAction[D <: EventDTO] {
  def wrapStream(in: InputStream): D
}

object Unarchiver {
  implicit val zipUnarchiver: UnarchiveAction[ZipEventDTO] =
    new UnarchiveAction[ZipEventDTO] {
      override def wrapStream(in: InputStream): ZipEventDTO =
        ZipEventDTO(new ZipInputStream(in))
    }

  implicit val tarGzipUnarchiver: UnarchiveAction[TarGZipEventDTO] =
    new UnarchiveAction[TarGZipEventDTO] {
      override def wrapStream(in: InputStream): TarGZipEventDTO =
        TarGZipEventDTO(
          new TarArchiveInputStream(new GzipCompressorInputStream(in)))
    }

  implicit val gzipUnarchiver: UnarchiveAction[GZipEventDTO] =
    new UnarchiveAction[GZipEventDTO] {
      override def wrapStream(in: InputStream): GZipEventDTO =
        GZipEventDTO(new GzipCompressorInputStream(in))
    }
}
