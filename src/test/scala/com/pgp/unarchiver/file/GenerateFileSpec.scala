package com.pgp.unarchiver.file

import java.io.{
  BufferedOutputStream,
  File,
  FileInputStream,
  FileOutputStream,
  OutputStream
}
import java.nio.file.Paths
import java.util.zip.{GZIPInputStream, ZipFile}

import com.pgp.unarchiver.FileSetup
import org.apache.commons.compress.archivers.tar.{
  TarArchiveEntry,
  TarArchiveOutputStream
}
import org.apache.commons.compress.archivers.zip.{
  ZipArchiveEntry,
  ZipArchiveOutputStream
}
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.utils.IOUtils
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class GenerateFileSpec extends WordSpec with Matchers with FileSetup {

  "A Generated File" when {

    "generated" should {

      val file1 = fileWithLines(100)
      val file2 = fileWithLines(100)

      val paths = for {
        path1 <- file1
        path2 <- file2
      } yield (path1, path2)

      val (path1, path2) = Await.result(paths, 600 seconds)

      "be zip archived" in {

        val zipFile = File.createTempFile("test-decryption-zip", ".zip")
        val zos = new ZipArchiveOutputStream(zipFile)
        zos.setMethod(ZipArchiveOutputStream.DEFLATED)

        Seq(path1, path2).map(p => Paths.get(p).toFile).foreach { f =>
          zos.putArchiveEntry(new ZipArchiveEntry(f.getName))
          val fis = new FileInputStream(f)
          IOUtils.copy(fis, zos)
          IOUtils.closeQuietly(fis)
          zos.closeArchiveEntry()
        }

        zos.flush()
        IOUtils.closeQuietly(zos)

        val zf = new ZipFile(zipFile.getAbsolutePath)
        zf.size() shouldBe 2
      }

      "be gunzip archived without exceptions" in {

        val zipFile = File.createTempFile("test-decryption-gz", ".gz")
        val zos = new GzipCompressorOutputStream(
          new BufferedOutputStream(new FileOutputStream(zipFile)))

        val fis = new FileInputStream(Paths.get(path1).toFile)

        IOUtils.copy(fis, zos)

        zos.flush()
        IOUtils.closeQuietly(zos)

        val zf =
          new GZIPInputStream(new FileInputStream(zipFile.getAbsolutePath))

        val ignored = new OutputStream {
          override def write(b: Int): Unit = {}
        }

        IOUtils.copy(zf, ignored)
      }

      "be tar.gz archived without exceptions" in {

        val zipFile = File.createTempFile("test-decryption-tar", ".tar.gz")
        val zos = new TarArchiveOutputStream(
          new GzipCompressorOutputStream(
            new BufferedOutputStream(new FileOutputStream(zipFile))))

        Seq(path1, path2).map(p => Paths.get(p).toFile).foreach { f =>
          val ent = new TarArchiveEntry(f.getName)
          ent.setSize(f.length())
          zos.putArchiveEntry(ent)
          val fis = new FileInputStream(f)
          IOUtils.copy(fis, zos)
          IOUtils.closeQuietly(fis)
          zos.closeArchiveEntry()
        }

        zos.flush()
        IOUtils.closeQuietly(zos)
      }
    }
  }

}
