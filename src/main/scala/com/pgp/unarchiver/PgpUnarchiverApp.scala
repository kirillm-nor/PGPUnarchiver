package com.pgp.unarchiver

import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import awscala.s3.S3
import awscala.{Credentials, Region}
import org.bouncycastle.jce.provider.BouncyCastleProvider

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class PipeConfig(awsSecret: String = "",
                      awsAccess: String = "",
                      awsRegion: Region = Region.default(),
                      keyRingPath: String = "",
                      passPhrase: String = "",
                      bucketName: String = "",
                      prefix: Option[String] = None)

/**
  * The main setup class which declares all configs and actor system.
  */
trait AppSetup {

  implicit val actorSystem: ActorSystem = ActorSystem("PGPUnarchiverSystem")
  implicit val materializer: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(actorSystem)
      .withSupervisionStrategy(Supervision.restartingDecider),
    "PGP")
  implicit val logger: LoggingAdapter = Logging.getLogger(actorSystem, getClass)

  val parser = new scopt.OptionParser[PipeConfig]("pgpunarchiver") {
    head("pgp unarchiver", "0.1")

    opt[String]('s', "secret")
      .required()
      .valueName("<secret>")
      .action((s, c) => c.copy(awsSecret = s))
      .validate(s => Either.cond(s.nonEmpty, Unit, "Secret shouldn't be blank"))
      .text("AWS Secret Key")

    opt[String]('a', "access")
      .required()
      .valueName("<access>")
      .action((a, c) => c.copy(awsAccess = a))
      .validate(s =>
        Either.cond(s.nonEmpty, Unit, "Access key shouldn't be blank"))
      .text("AWS Access Key")

    opt[String]('r', "region")
      .action((r, c) => c.copy(awsRegion = Region(r)))
      .validate(r =>
        Try(Region(r)) match {
          case Success(_) => success
          case Failure(e) => failure(e.getMessage)
      })
      .text("AWS Region")

    opt[String]('p', "path")
      .required()
      .valueName("<file>")
      .action((p, c) => c.copy(keyRingPath = p))
      .validate(p =>
        Either.cond(Paths.get(p).toFile.exists(), Unit, "File not exists"))
      .text("GPG KeyRing file path")

    opt[String]('b', "bucket")
      .required()
      .valueName("<bucket>")
      .action((b, c) => c.copy(bucketName = b))
      .validate(b => Either.cond(b.nonEmpty, Unit, "Bucket shouldn't be blank"))
      .text("AWS Bucket Name")

    opt[String]("phrase")
      .required()
      .valueName("<phrase>")
      .action((p, c) => c.copy(passPhrase = p))
      .validate(p => Either.cond(p.nonEmpty, Unit, "Phrase shouldn't be empty"))
      .text("Pass phrase of pgp")

    opt[String]("prefix")
      .valueName("<value>")
      .action((p, c) => c.copy(prefix = Some(p)))
      .validate(p => Either.cond(p.nonEmpty, Unit, "Prefix shouldn't be empty"))
      .text("S3 prefix")
  }
}

object PgpUnarchiverApp extends App with AppSetup {

  parser.parse(args, PipeConfig()) match {
    case Some(pc) =>
      java.security.Security.addProvider(new BouncyCastleProvider)

      import actorSystem.dispatcher

      implicit val s3Client: S3 =
        S3(Credentials(pc.awsAccess, pc.awsSecret))(pc.awsRegion)

      val pipe =
        PgpUnarchiverPipe(pc.bucketName,
                          Paths.get(pc.keyRingPath),
                          pc.passPhrase,
                          pc.prefix)
      pipe.compareCheckSum
        .flatMap {
          case true => pipe.decryptionFlow
          case false =>
            Future.failed(new InterruptedException("Checksum invalid"))
        }
        .onComplete {
          case Success(_) => logger.info("Stream completed")
          case Failure(ex) =>
            logger.error("Exceptionally close application", ex)
            ex.printStackTrace()
            System.exit(0)
        }
    case None =>
      logger.error("Exceptionally close application")
      System.exit(0)
  }

}
