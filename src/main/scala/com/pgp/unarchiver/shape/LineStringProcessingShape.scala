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

            def grabUntil(b: ByteString, acc: Seq[String]): Seq[String] = {
              b.span(b => b != nl) match {
                case (l, r) if l.isEmpty && acc.isEmpty =>
                  val e = grab(in)
                  if (e.isEmpty) {
                    rest = ByteString()
                    Seq(new String(r.toArray))
                  } else grabUntil(r ++ e, Seq())
                case (l, r) if l.isEmpty =>
                  rest = r
                  acc
                case (l, r) if r.isEmpty =>
                  rest = ByteString()
                  acc :+ new String(l.toArray)
                case (l, r) => grabUntil(r, acc :+ new String(l.toArray))
              }
            }

            val elems = grabUntil(rest ++ grab(in), Seq())
            emitMultiple(out, elems.toIterator)
          }
        }
      )

      setHandler(out, new OutHandler {
        override def onPull(): Unit = pull(in)
      })
    }

}
