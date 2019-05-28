package com.pgp.unarchiver.shape

import akka.stream.Attributes.Name
import akka.stream._
import akka.stream.scaladsl.Sink
import akka.stream.stage.{
  GraphStageLogic,
  GraphStageWithMaterializedValue,
  InHandler,
  StageLogging
}
import akka.util.ByteString

import scala.concurrent.{Future, Promise}

object CheckSumShape {
  def sink: Sink[ByteString, Future[String]] =
    Sink
      .fromGraph(new CheckSumShape)
      .withAttributes(Attributes(Name("checkSum")))
}

/**
  * Akka Stream Sink shape to  calculate checksum of file, based on MD5 algorithm
  */
class CheckSumShape
    extends GraphStageWithMaterializedValue[SinkShape[ByteString],
                                            Future[String]] {

  val in = Inlet[ByteString]("CheckSumShape.in")
  override val shape: SinkShape[ByteString] = SinkShape.of(in)

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes): (GraphStageLogic, Future[String]) = {
    val p: Promise[String] = Promise()
    (new GraphStageLogic(shape) with InHandler with StageLogging {
      val digest = java.security.MessageDigest.getInstance("MD5")

      override def preStart(): Unit = pull(in)

      override def onPush(): Unit = {
        val e = grab(in)
        digest.update(e.toArray)
        pull(in)
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        super.onUpstreamFailure(ex)
        p.tryFailure(ex)
      }

      override def onUpstreamFinish(): Unit = {
        super.onUpstreamFinish()
        p.trySuccess(digest.digest().map("%02x".format(_)).mkString)
        log.debug(
          s"Checksum calculation completed is ${digest.digest().map("%02x".format(_)).mkString}")
        completeStage()
      }

      override def postStop(): Unit = {
        if (!p.isCompleted) p.failure(new AbruptStageTerminationException(this))
      }

      setHandler(in, this)
    }, p.future)
  }

}
