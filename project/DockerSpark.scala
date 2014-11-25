import sbt._
import sbt.Keys._
import sbtassembly.Plugin._
import AssemblyKeys._
import sbtbuildinfo.Plugin._
import sbtrelease.ReleasePlugin._
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform._
import scoverage.ScoverageSbtPlugin
import net.virtualvoid.sbt.graph.Plugin._
import sbtdocker.Plugin._
import sbtdocker.Plugin.DockerKeys._
import sbtdocker.mutable.Dockerfile
import sbtdocker.ImageName

object DockerSpark extends Build {

  lazy val basicDependencies: Seq[Setting[_]] = Seq(
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13",
    libraryDependencies += "com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
    libraryDependencies += "org.apache.spark" %% "spark-core" % "1.1.0"
      exclude("org.slf4j", "slf4j-log4j12")
      exclude("org.eclipse.jetty.orbit", "javax.transaction")
      exclude("org.eclipse.jetty.orbit", "javax.mail")
      exclude("org.eclipse.jetty.orbit", "javax.mail.glassfish")
      exclude("org.eclipse.jetty.orbit", "javax.activation")
      exclude("commons-beanutils", "commons-beanutils-core")
      exclude("commons-collections", "commons-collections")
      exclude("commons-logging", "commons-logging")
      exclude("com.esotericsoftware.minlog", "minlog"),
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
    settings = Project.defaultSettings ++ basicDependencies ++ graphSettings ++ assemblySettings ++ dockerSettings ++ releaseSettings ++ scalariformSettingsWithIt ++ itRunSettings ++ testDependencies ++ buildInfoSettings ++ Seq(
      name := "docker-spark",
      organization := "eu.stupidsoup.spark",
      scalaVersion := "2.10.4",
      ScalariformKeys.preferences := formattingSettings,
      ScoverageSbtPlugin.ScoverageKeys.highlighting := true,
      // build info
      sourceGenerators in Compile <+= buildInfo,
      buildInfoKeys := Seq[BuildInfoKey](name, version),
      buildInfoPackage := "eu.stupidsoup.spark.info",
      // assembly
      jarName in assembly <<= (name, version) map ( (n, v) => s"$n-$v.jar" ),
      mainClass in assembly := Option("eu.stupidsoup.spark.DockerMain"),
      mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
        case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
        case PathList("javax", "transaction", xs @ _*)     => MergeStrategy.first
        case PathList("javax", "mail", xs @ _*)     => MergeStrategy.first
        case PathList("javax", "activation", xs @ _*)     => MergeStrategy.first
        case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
        case "application.conf" => MergeStrategy.concat
        case "unwanted.txt"     => MergeStrategy.discard
        case x => old(x)
      }},
      // docker
      docker <<= (docker dependsOn assembly),
      imageName in docker := {
        ImageName(
          namespace = Some(organization.value),
          repository = name.value,
          tag = Some("v" + version.value)
        )
      },
      dockerfile in docker := {
        val artifact = (outputPath in assembly).value
        val artifactTargetPath = s"/app/${artifact.name}"
        new Dockerfile {
          from("dockerfile/java")
          add(artifact, artifactTargetPath)
          entryPoint("java", "-jar", artifactTargetPath)
        }
      },
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
