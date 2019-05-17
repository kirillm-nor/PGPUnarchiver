package com.pgp.unarchiver.archive

import java.io.InputStream
import java.util.zip.ZipInputStream

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

sealed trait EventDTO {
  def stream: InputStream
}


/**
  * Type class which contains unarchive directives.
  */
sealed abstract class UnarchiveEventAction {
  type UnarchiveDTO <: EventDTO
}

object EventAction {
  case object ZipUnarchiveEventAction extends UnarchiveEventAction {
    override type UnarchiveDTO = ZipEventDTO
    type ZipUnarchiveEventAction = ZipUnarchiveEventAction.type
  }

  case object TarGZipUnarchiveEventAction extends UnarchiveEventAction {
    override type UnarchiveDTO = TarGZipEventDTO
    type TarGZipUnarchiveEventAction = TarGZipUnarchiveEventAction.type
  }

  case object GZipUnarchiveEventAction extends UnarchiveEventAction {
    override type UnarchiveDTO = GZipEventDTO
    type GZipUnarchiveEventAction = GZipUnarchiveEventAction.type
  }
}

case class ZipEventDTO(stream: ZipArchiveInputStream) extends EventDTO
case class TarGZipEventDTO(stream: TarArchiveInputStream) extends EventDTO
case class GZipEventDTO(stream: GzipCompressorInputStream) extends EventDTO
