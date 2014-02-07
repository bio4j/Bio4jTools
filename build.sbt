Nice.javaProject

name := "bio4j-examples"

description := "Examples of Bio4j usage"

organization := "ohnosequences"

bucketSuffix := "era7.com"

docsInputDir := baseDirectory.value + "/src/main/java/com/era7/bioinfo/bio4j/tools/"

docsOutputDir := "docs/src/"

libraryDependencies ++= Seq(
  "ohnosequences" % "bio4j-model" % "0.3.2"
)
