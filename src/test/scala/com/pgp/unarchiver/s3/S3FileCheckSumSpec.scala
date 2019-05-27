package com.pgp.unarchiver.s3

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.ActorMaterializer
import awscala.s3.S3
import com.amazonaws.regions.{Region, Regions}
import com.pgp.unarchiver.{PgpUnarchiverPipe, S3Setup}
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

class S3FileCheckSumSpec extends WordSpec with Matchers with S3Setup {

  implicit val actorSystem: ActorSystem = ActorSystem("PGPUnarchiverSystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val logger: LoggingAdapter = Logging.getLogger(actorSystem, getClass)
  implicit val s3Client: S3 =
    S3("<>", "<>")(Region.getRegion(Regions.US_WEST_2))

  "Uploaded file" when {
    val md5 = "48f7a85e2c7fbece8fcb11bac9ab1239"
    "calculate md5 checksum" should {
      def uploadFile = {
        val file =
          Paths.get(getClass.getResource("/trustedkeys.gpg").toURI).toFile

        Await.ready(this.uploadFile(s"upload/${file.getName}.zip.pgp",
                                    "beam-config",
                                    file),
                    30 seconds)
      }

      "be filtered" in {
        uploadFile

        val source = new S3FileSource("beam-config", "checksum", Some("upload"))
        val files = Await.result(source.filesMeta, 60 seconds)

        files.size shouldBe 1
      }

      "be calculated" in {
        uploadFile

        val source = new S3FileSource("beam-config", "checksum", Some("upload"))
        val files = Await.result(source.filesCheckSums, 60 seconds)

        files.head._2 shouldBe md5
      }

      "be compared" in {
        import actorSystem.dispatcher

        uploadFile

        val pipe = PgpUnarchiverPipe(
          "beam-config",
          Paths.get(getClass.getResource("/trustedkeys.gpg").toURI),
          "<>",
          Some("upload")).compareCheckSum.recover {
          case ex =>
            println(ex)
            false
        }

        val res = Await.result(pipe, 60 seconds)

        res shouldBe true
      }
    }
  }
}
