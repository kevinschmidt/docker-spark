import sbt._
import sbt.Keys._
import sbtbuildinfo.Plugin._
import sbtrelease.ReleasePlugin._
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform._
import scoverage.ScoverageSbtPlugin


object DockerSpark extends Build {

  lazy val basicDependencies: Seq[Setting[_]] = Seq(
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13",
    libraryDependencies += "com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
    libraryDependencies += "org.apache.spark" %% "spark-core" % "1.1.0",
    libraryDependencies += "org.apache.spark" %% "spark-streaming" % "1.1.0",
    libraryDependencies += "org.apache.spark" %% "spark-streaming-kafka" % "1.1.0"
  )

  lazy val testDependencies: Seq[Setting[_]] = Seq(
    libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.10.1" % "test,it",
    libraryDependencies += "org.mockito" % "mockito-all" % "1.9.5" % "test,it",
    libraryDependencies += "org.specs2" %% "specs2" % "2.3.8" % "test,it",
    libraryDependencies += "junit" % "junit" % "4.11" % "test,it"
  )

  lazy val formattingSettings = FormattingPreferences()
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, false)
    .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 40)
    .setPreference(CompactControlReadability, false)
    .setPreference(CompactStringConcatenation, false)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(FormatXml, true)
    .setPreference(IndentLocalDefs, false)
    .setPreference(IndentPackageBlocks, true)
    .setPreference(IndentSpaces, 2)
    .setPreference(IndentWithTabs, false)
    .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
    .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, false)
    .setPreference(PreserveSpaceBeforeArguments, false)
    .setPreference(PreserveDanglingCloseParenthesis, true)
    .setPreference(RewriteArrowSymbols, false)
    .setPreference(SpaceBeforeColon, false)
    .setPreference(SpaceInsideBrackets, false)
    .setPreference(SpaceInsideParentheses, false)
    .setPreference(SpacesWithinPatternBinders, true)

  lazy val itRunSettings = Seq(
    fork in IntegrationTest := true,
    connectInput in IntegrationTest := true
  )

  def vcsNumber: String = {
    val vcsBuildNumber = System.getenv("BUILD_VCS_NUMBER")
    if (vcsBuildNumber == null) "" else vcsBuildNumber
  }

  lazy val forkedJVMOption = Seq("-Duser.timezone=UTC")

  lazy val waterfall = Project(
    id = "docker-spark",
    base = file("."),
    settings = Project.defaultSettings ++ basicDependencies ++ releaseSettings ++ scalariformSettingsWithIt ++ itRunSettings ++ testDependencies ++ buildInfoSettings ++ Seq(
      name := "docker-spark",
      organization := "com.mindcandy.spark",
      scalaVersion := "2.10.4",
      ScalariformKeys.preferences := formattingSettings,
      publishTo <<= (version) { version: String =>
        val repo = "http://artifactory.tool.mindcandy.com/artifactory/"
        val revisionProperty = if (!vcsNumber.isEmpty) ";revision=" + vcsNumber else ""
        val timestampProperty = ";build.timestamp=" + new java.util.Date().getTime
        val props = timestampProperty + revisionProperty
        if (version.trim.endsWith("SNAPSHOT"))
          Some("snapshots" at repo + "libs-snapshot-local" + props)
        else
          Some("releases" at repo + "libs-release-local" + props)
      },
      ScoverageSbtPlugin.ScoverageKeys.highlighting := true,
      // build info
      sourceGenerators in Compile <+= buildInfo,
      buildInfoKeys := Seq[BuildInfoKey](name, version),
      buildInfoPackage := "com.mindcandy.spark.info",
      // forked JVM
      fork in run := true,
      fork in Test := true,
      fork in testOnly := true,
      javaOptions in run ++= forkedJVMOption,
      javaOptions in Test ++= forkedJVMOption,
      javaOptions in testOnly ++= forkedJVMOption
    )
  ).configs( IntegrationTest )
   .settings( ScoverageSbtPlugin.instrumentSettings ++ Defaults.itSettings ++ Seq(unmanagedSourceDirectories in IntegrationTest <++= { baseDirectory { base => { Seq( base / "src/test/scala" )}}}) : _*)

}

