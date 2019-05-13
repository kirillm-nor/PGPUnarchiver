package com.pgp.unarchiver.pgp

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.ByteRange
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, Outlet, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, OutHandler, StageLogging}
import akka.util.ByteString
import com.pgp.unarchiver.pgp.PGPSourceShape.{Base64HeadersPGPNotSupported, Base64PGPNotSupported}
import org.bouncycastle.util.encoders.Base64

import scala.concurrent.Await
import scala.concurrent.duration._

object PGPSourceShape {
  case object Base64HeadersPGPNotSupported extends Exception
  case object Base64PGPNotSupported extends Exception

  def apply(bucket: String, fileKey: String, fileSize: Long)(implicit materialiser: ActorMaterializer,
                                                             system: ActorSystem): Source[Done, NotUsed] =
    Source.fromGraph(new PGPSourceShape(bucket, fileKey, fileSize))
}

class PGPSourceShape(bucket: String, fileKey: String, fileSize: Long)(implicit materialiser: ActorMaterializer,
                                                                      system: ActorSystem) extends GraphStage[SourceShape[Done]] {

  import system.dispatcher

  val out: Outlet[Done] = Outlet[Done]("PGPSourceShape.out")

  override val shape: SourceShape[Done] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging with OutHandler {

      private def isPossiblyBase64(ch: Byte) = (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || (ch == '+') || (ch == '/') || (ch == '\r') || (ch == '\n')

      override def onPull(): Unit = {
        val bArrFuture = Source.fromFutureSource(S3.download(bucket, fileKey, Some(ByteRange(0, 60))).runWith(Sink.head)
          .collect {
            case Some((data, _)) => data
          }).runWith(Sink.fold(ByteString.empty)(_ ++ _)).map(_.toArray)
        val arr = Await.result(bArrFuture, 5 seconds)
        if ((arr.head & 0x80) != 0) {
          push(out, Done)
        } else {
          arr.partition(isPossiblyBase64) match {
            case (_, a) if a.isEmpty => failStage(Base64HeadersPGPNotSupported)
            case (c, _) if c.length < 4 => failStage(Base64HeadersPGPNotSupported)
            case (c, _) =>
              val block = c.filter(ch => ch != '\n' && ch != '\r').take(8)
              if ((Base64.decode(block).head & 0x80) != 0) failStage(Base64PGPNotSupported)
              else failStage(Base64HeadersPGPNotSupported)
          }
        }
      }

      setHandler(out, this)

    }

}
