package com.pgp.unarchiver.archive

import java.io.InputStream
import java.util.zip.ZipInputStream

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

sealed trait EventDTO {
  def stream: InputStream
}

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
}

case class ZipEventDTO(stream: ZipInputStream) extends EventDTO
case class TarGZipEventDTO(stream: TarArchiveInputStream) extends EventDTO
