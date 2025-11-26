scalaVersion := "3.7.3"

name := "server"

organization := "org.felher"

enablePlugins(JavaAppPackaging)

scalacOptions ++= Seq(
  "-language:strictEquality",
  "-source:future",
  "-feature",
  "-deprecation",
  "-Xkind-projector:underscores",
  "-Wsafe-init",
  "-Xmax-inlines:256",
  "-language:implicitConversions",
  "-Wunused:all",
  "-Wvalue-discard"
)

resolvers += Resolver.sonatypeRepo("releases")
resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += "Olyro Artifactory" at "https://nexus.olyro.de/artifactory/libs-snapshot-local/"
credentials += Credentials(Path.userHome / ".m2" / "sbt-credentials")

libraryDependencies ++= Seq(
  "org.typelevel"        %% "cats-core"           % "2.10.0",
  "org.typelevel"        %% "alleycats-core"      % "2.10.0",
  "org.typelevel"        %% "cats-effect"         % "3.5.4",
  "org.typelevel"        %% "cats-mtl"            % "1.3.0",
  "io.circe"             %% "circe-core"          % "0.14.6",
  "io.circe"             %% "circe-generic"       % "0.14.6",
  "io.circe"             %% "circe-parser"        % "0.14.6",
  "org.http4s"           %% "http4s-blaze-server" % "0.23.16",
  "org.http4s"           %% "http4s-circe"        % "0.23.26",
  "org.http4s"           %% "http4s-dsl"          % "0.23.26",
  "ch.qos.logback"        % "logback-classic"     % "1.3.0-alpha5",
  "org.tpolecat"         %% "doobie-core"         % "1.0.0-RC5",
  "org.tpolecat"         %% "doobie-postgres"     % "1.0.0-RC5",
  "org.tpolecat"         %% "doobie-hikari"       % "1.0.0-RC5",
  "com.github.jwt-scala" %% "jwt-circe"           % "10.0.0",
  "dev.optics"           %% "monocle-core"        % "3.1.0",
  "dev.optics"           %% "monocle-macro"       % "3.1.0",
  "org.docx4j"            % "docx4j-JAXB-MOXy"    % "11.1.2",
  "dev.zio"              %% "zio"                 % "2.1-RC1",
  "dev.zio"              %% "zio-test"            % "2.1-RC1",
  "dev.zio"              %% "zio-streams"         % "2.1-RC1",
  "dev.zio"              %% "zio-test-sbt"        % "2.1-RC1",
  "dev.zio"              %% "zio-interop-cats"    % "23.1.0.1",
  "org.apache.jena"       % "apache-jena-libs"    % "3.13.1",
  "org.commonmark"        % "commonmark"          % "0.27.0",
  "org.commonmark"        % "commonmark-ext-gfm-tables"          % "0.27.0",
  ("com.lihaoyi"         %% "scalatags"           % "0.12.0").cross(CrossVersion.for3Use2_13),
  ("com.lihaoyi"         %% "pprint"              % "0.8.1").cross(CrossVersion.for3Use2_13),
  ("org.scalameta"       %% "scalameta"           % "4.8.14").cross(CrossVersion.for3Use2_13)
)

assemblyMergeStrategy in assembly := {
  case "module-info.class" => MergeStrategy.discard
  case x                   =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
