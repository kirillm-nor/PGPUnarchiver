name := "PGPUnarchiver"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "1.0.0",
  "org.bouncycastle" % "bcpg-jdk15on" % "1.61",
  "com.github.seratch" %% "awscala-s3" % "0.8.2",
  "org.apache.commons" % "commons-compress" % "1.18",
  "com.github.scopt" %% "scopt" % "3.7.1",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test")

assemblyJarName in assembly := "pgpunarchiver-fat.jar"

test in assembly := {}

mainClass in assembly := Some("com.pgp.unarchiver.PgpUnarchiverApp")

assemblyMergeStrategy in assembly := {
  case PathList("javax", "servlet", xs @ _*)                                => MergeStrategy.first
  case PathList("META-INF", ps @ _*) if ps.nonEmpty && (ps.last endsWith ".RSA") => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".html"                        => MergeStrategy.first
  case "application.conf"                                                   => MergeStrategy.concat
  case "version.conf"                                                       => MergeStrategy.concat
  case "reference.conf"                                                     => MergeStrategy.concat
  case "module-info.class"                                                     => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}