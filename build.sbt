name := "PGPUnarchiver"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "1.0.0",
  "org.bouncycastle" % "bcpg-jdk15on" % "1.61",
  "com.github.seratch" %% "awscala-s3" % "0.8.2")

