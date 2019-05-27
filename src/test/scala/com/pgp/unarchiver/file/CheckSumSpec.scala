package com.pgp.unarchiver.file

import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Sink}
import com.pgp.unarchiver.shape.CheckSumShape
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class CheckSumSpec extends WordSpec with Matchers {

  "Resource file trustedkeys.gpg" when {

    implicit val actorSystem: ActorSystem = ActorSystem("PGPUnarchiverSystem")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val logger: LoggingAdapter =
      Logging.getLogger(actorSystem, getClass)

    "calculate MD5 checksum" should {
      "be compare" in {
        val md5 = "48f7a85e2c7fbece8fcb11bac9ab1239"

        val file = getClass.getResource("/trustedkeys.gpg").toURI

        Paths.get(file).toFile.exists() shouldBe true

        val check = FileIO
          .fromPath(Paths.get(file))
          .runWith(CheckSumShape.sink)

        val sum = Await.result(check, 60 seconds)

        md5 shouldEqual sum
      }
    }
  }
}
