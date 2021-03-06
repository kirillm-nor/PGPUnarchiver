package com.pgp.unarchiver.shape

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString

object LineStringProcessingShape {
  def apply: Flow[ByteString, String, NotUsed] =
    Flow.fromGraph(new LineStringProcessingShape())
}

class LineStringProcessingShape
    extends GraphStage[FlowShape[ByteString, String]] {

  val in: Inlet[ByteString] = Inlet[ByteString]("LineStringProcessingShape.in")
  val out: Outlet[String] = Outlet[String]("LineStringProcessingShape.out")

  override val shape: FlowShape[ByteString, String] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {

      private val nl: Byte = '\n'
      private var rest: ByteString = ByteString()

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {

            def grabUntil(b: ByteString): Seq[String] = {
              b.span(b => b != nl) match {
                case (l, r) if l.isEmpty =>
                  rest = r.tail
                  Seq()
                case (l, r) if r.isEmpty =>
                  rest = l
                  Seq()
                case (l, r) =>
                  new String(l.filter(b => b != 0).toArray) +: grabUntil(r.tail)
              }
            }

            grabUntil(rest ++ grab(in)) match {
              case Nil   => pull(in)
              case elems => emitMultiple(out, elems.toIterator)
            }
          }

          override def onUpstreamFinish(): Unit = {
            push(out, new String(rest.filter(b => b != 0).toArray))
            log.debug("All lines was wrapped")
            completeStage()
          }
        }
      )

      setHandler(out, new OutHandler {
        override def onPull(): Unit = pull(in)
      })
    }

}
