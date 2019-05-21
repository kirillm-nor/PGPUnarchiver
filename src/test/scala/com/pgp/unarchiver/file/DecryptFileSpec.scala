package com.pgp.unarchiver.file

import java.io.{File, FileInputStream}

import akka.Done
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.util.ByteString
import com.pgp.unarchiver.FileSetup
import com.pgp.unarchiver.pgp.{
  PGPFileDecryptUnarchiveSource,
  PGPLocalPrivateKey
}
import com.pgp.unarchiver.s3.ZIP
import com.pgp.unarchiver.shape.ByteStringProcessShape
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class DecryptFileSpec extends WordSpec with Matchers with FileSetup {

  implicit val actorSystem: ActorSystem = ActorSystem("PGPUnarchiverSystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val logger: LoggingAdapter = Logging.getLogger(actorSystem, getClass)

  "An Encrypted File" when {
    java.security.Security.addProvider(new BouncyCastleProvider)

    val file = PGPLocalPrivateKey(
      new File(getClass.getResource("/trustedkeys.gpg").toURI))

    "key exists" in {
      val pk =
        file.getPrivateKey(java.lang.Long.parseLong("4D074426C37BACF3", 16),
                           "kirmit".toCharArray)

      pk.isDefined shouldBe true
    }

    "zip archive" should {

      "be binary content extracted as string" in {
        val is = new FileInputStream(
          "/var/folders/w3/jdgp87j15rv_fv4f9mvn85340000gn/T/test-decryption-zip8259292171991400919.zip.gpg")

        val dc =
          PGPFileDecryptUnarchiveSource(is, ZIP, file, "kirmit".toCharArray)
            .runForeach(s => println(new String(s.toArray)))

        val res = Await.result(dc, 600 seconds)

        res shouldBe Done
      }

      "be armored content extracted as string" in {}

    }

    "gunzip archive" should {

      "be binary content extracted as string" in {}

      "be armored content extracted as string" in {}

    }

    "tar.gz archive" should {

      "be binary content extracted as string" in {}

      "be armored content extracted as string" in {}

    }
  }

}
