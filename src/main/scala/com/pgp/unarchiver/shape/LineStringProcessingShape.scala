package com.pgp.unarchiver.shape

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage._
import akka.util.ByteString

import scala.collection.mutable

class LineStringProcessingShape
    extends GraphStage[FlowShape[ByteString, String]] {

  val in: Inlet[ByteString] = Inlet[ByteString]("LineStringProcessingShape.in")
  val out: Outlet[String] = Outlet[String]("LineStringProcessingShape.out")

  override val shape: FlowShape[ByteString, String] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {

      private val stringQueue: mutable.Seq[String] = mutable.Seq()
      private val nl: Byte = '\n'
      private var rest: ByteString = ByteString()

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {
            val el = grab(in)

            (rest ++ grab(in)).span(b => b != nl) match {
              case (l, r) if l.isEmpty => r.span(b => b != nl)
              case (l, r) if r.isEmpty => grab(in).span(b => b != nl)
              case (l, r) =>
                rest = r
                push(out, new String(l.toArray))
            }

          }
        }
      )

      setHandler(out, new OutHandler {
        override def onPull(): Unit = pull(in)
      })
    }

}
