name := "sha1Perf"

version := "0.1-SNAPSHOT"

scalaVersion := "2.9.2"

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test"

libraryDependencies += "com.vidal.glow" % "glow-all" % "0.4.1-SNAPSHOT"
