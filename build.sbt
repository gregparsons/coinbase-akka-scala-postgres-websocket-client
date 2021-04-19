name := "scala_websocket"

version := "0.1"

scalaVersion := "2.13.5"


lazy val akkaVersion = "2.6.14"
lazy val akkaHttpVersion = "10.2.4"

libraryDependencies ++= Seq(
	"org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
	// "ch.qos.logback" % "logback-classic" % "1.2.3",
	"org.scalatest" %% "scalatest" % "3.1.0" % Test,
	"com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
	"com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
	// for scheduler
	"com.typesafe.akka" %% "akka-actor" % akkaVersion,
	"com.typesafe.akka" %% "akka-stream" % akkaVersion,
	"com.typesafe.akka" %% "akka-http" % akkaHttpVersion,


	// https://github.com/andyglow/websocket-scala-client
	"com.github.andyglow" %% "websocket-scala-client" % "0.3.0" % Compile,


	// json
	// https://www.lihaoyi.com/post/HowtoworkwithJSONinScala.html
	"com.lihaoyi" %% "upickle" % "1.3.11"





)

