name := """formBuilderServer"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += "ruimo.com" at "http://static.ruimo.com/release"

scalaVersion := "2.12.3"

libraryDependencies += guice
libraryDependencies += jdbc
libraryDependencies += evolutions
libraryDependencies += "com.ruimo" %% "graphics" % "1.2"
libraryDependencies += "org.postgresql" % "postgresql" % "42.1.3"
libraryDependencies += "com.h2database" % "h2" % "1.4.196" % "test"
libraryDependencies += "com.ruimo" %% "scoins" % "1.12"
libraryDependencies += "org.scalikejdbc" %% "scalikejdbc" % "3.0.1"
libraryDependencies += "org.scalikejdbc" %% "scalikejdbc-config" % "3.0.1"
libraryDependencies += "org.scalikejdbc" %% "scalikejdbc-test" % "3.0.1"
libraryDependencies += "org.scalikejdbc" %% "scalikejdbc-play-initializer" % "2.6.0"
libraryDependencies += specs2 % Test

scalikejdbcSettings
