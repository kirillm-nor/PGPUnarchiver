package com.pgp.unarchiver.shape

import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString

/**
  * Stream Flow which applies provided function to array of bytes
  *
  * @param f the function to proceed incoming byte array
  * @tparam A result type
  */
class ByteStringProcessShape[A](f: ByteString => A)
    extends GraphStage[FlowShape[ByteString, A]] {

  val in: Inlet[ByteString] = Inlet[ByteString]("ByteStringProcessShape.in")
  val out: Outlet[A] = Outlet[A]("ByteStringProcessShape.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val elem = grab(in)
          log.debug(s"Processing byte array $elem")
          push(out, f(elem))
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = pull(in)
      })
    }

}
