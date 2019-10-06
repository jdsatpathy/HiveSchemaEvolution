name := "HiveSchemaEvolution"

version := "0.1"

scalaVersion := "2.11.12"

libraryDependencies+="org.apache.spark" %% "spark-core" % "2.3.0"
libraryDependencies+= "org.apache.spark" %% "spark-sql" % "2.3.2"
libraryDependencies+= "com.typesafe" % "config" % "1.2.1"
// https://mvnrepository.com/artifact/com.typesafe.scala-logging/scala-logging
libraryDependencies+= "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}