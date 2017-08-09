// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.1")

libraryDependencies += "org.postgresql" % "postgresql" % "42.1.3"

addSbtPlugin("org.scalikejdbc" % "scalikejdbc-mapper-generator" % "3.0.1")
