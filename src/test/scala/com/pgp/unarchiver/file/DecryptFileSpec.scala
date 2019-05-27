package com.pgp.unarchiver.file

import java.io.{File, FileInputStream}

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.ActorMaterializer
import com.pgp.unarchiver.FileSetup
import com.pgp.unarchiver.pgp.{
  PGPFileDecryptUnarchiveSource,
  PGPLocalPrivateKey
}
import com.pgp.unarchiver.s3.{GZ, TAR_GZ, ZIP}
import com.pgp.unarchiver.shape.LineStringProcessingShape
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class DecryptFileSpec extends WordSpec with Matchers with FileSetup {

  implicit val actorSystem: ActorSystem = ActorSystem("PGPUnarchiverSystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val logger: LoggingAdapter = Logging.getLogger(actorSystem, getClass)

  /**
    * To create armored decrypted file
    * gpg2 -a -r 0xBE09B314D91775AB -o {output_file_name}  -e {file_path}
    *
    * To create binary decrypted file
    * * gpg2 -r 0xBE09B314D91775AB -o {output_file_name}  -e {file_path}
    */
  "An Encrypted File" when {
    java.security.Security.addProvider(new BouncyCastleProvider)

    val file = PGPLocalPrivateKey(
      new File(getClass.getResource("/trustedkeys.gpg").toURI))

    "key exists" in {
      val pk =
        file.getPrivateKey(java.lang.Long.parseLong("4D074426C37BACF3", 16),
                           "<>".toCharArray)

      pk.isDefined shouldBe true
    }

    "zip archive" should {

      "be binary content extracted as string" in {
        val is = new FileInputStream(
          "/var/folders/w3/jdgp87j15rv_fv4f9mvn85340000gn/T/test-decryption-zip8784710522144013631.zip.gpg")

        val dc =
          PGPFileDecryptUnarchiveSource(is, ZIP, file, "<>".toCharArray)
            .via(LineStringProcessingShape.apply)
            .runFold(0)((acc, _) => acc + 1)

        val res = Await.result(dc, 600 seconds)

        res shouldBe 200
      }

      "be armored content extracted as string" in {
        val is = new FileInputStream(
          "/var/folders/w3/jdgp87j15rv_fv4f9mvn85340000gn/T/test-decryption-zip8784710522144013631.zip.gpg.a")

        val dc =
          PGPFileDecryptUnarchiveSource(is, ZIP, file, "<>".toCharArray)
            .via(LineStringProcessingShape.apply)
            .runFold(0)((acc, _) => acc + 1)

        val res = Await.result(dc, 600 seconds)

        res shouldBe 200
      }

    }

    "gunzip archive" should {

      "be binary content extracted as string" in {
        val is = new FileInputStream(
          "/var/folders/w3/jdgp87j15rv_fv4f9mvn85340000gn/T/test-decryption-gz6625984903750650281.gz.gpg")

        val dc =
          PGPFileDecryptUnarchiveSource(is, GZ, file, "<>".toCharArray)
            .via(LineStringProcessingShape.apply)
            .runFold(0)((acc, _) => acc + 1)

        val res = Await.result(dc, 600 seconds)

        res shouldBe 100
      }

      "be armored content extracted as string" in {
        val is = new FileInputStream(
          "/var/folders/w3/jdgp87j15rv_fv4f9mvn85340000gn/T/test-decryption-gz6625984903750650281.gz.gpg.a")

        val dc =
          PGPFileDecryptUnarchiveSource(is, GZ, file, "<>".toCharArray)
            .via(LineStringProcessingShape.apply)
            .runFold(0)((acc, _) => acc + 1)

        val res = Await.result(dc, 600 seconds)

        res shouldBe 100
      }

    }

    "tar.gz archive" should {

      "be binary content extracted as string" in {
        val is = new FileInputStream(
          "/var/folders/w3/jdgp87j15rv_fv4f9mvn85340000gn/T/test-decryption-tar9221538800511825546.tar.gz.gpg")

        val dc =
          PGPFileDecryptUnarchiveSource(is, TAR_GZ, file, "<>".toCharArray)
            .via(LineStringProcessingShape.apply)
            .runFold(0)((acc, _) => acc + 1)

        val res = Await.result(dc, 600 seconds)

        res shouldBe 200
      }

      "be armored content extracted as string" in {
        val is = new FileInputStream(
          "/var/folders/w3/jdgp87j15rv_fv4f9mvn85340000gn/T/test-decryption-tar9221538800511825546.tar.gz.gpg.a")

        val dc =
          PGPFileDecryptUnarchiveSource(is, TAR_GZ, file, "<>".toCharArray)
            .via(LineStringProcessingShape.apply)
            .runFold(0)((acc, _) => acc + 1)

        val res = Await.result(dc, 600 seconds)

        res shouldBe 200
      }

    }
  }

}
