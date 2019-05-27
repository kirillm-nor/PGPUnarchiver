package com.pgp.unarchiver.archive

import java.io.InputStream

trait StreamSeeker {
  self: InputStream =>
  def seek(b: Array[Byte], off: Int, len: Int): Option[Int]
}

sealed trait EventDTO {
  def stream: StreamSeeker
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

case class ZipEventDTO(stream: StreamSeeker) extends EventDTO

case class TarGZipEventDTO(stream: StreamSeeker) extends EventDTO

case class GZipEventDTO(stream: StreamSeeker) extends EventDTO
