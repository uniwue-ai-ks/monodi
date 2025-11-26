package de.olyro.monodi

import zio.*
import scala.sys.process.*

class ContainerEngine private (engine: ContainerEngine.Engine):
  def run(
      name: String,
      mounts: List[(String, String)],
      command: Seq[String]
  ): Task[Array[Byte]] = ZIO.scoped:
    val mountArgs = mounts.flatMap((from, to) => Seq("-v", s"$from:$to${engine.mountSuffix}"))
    val netArgs   = Seq("--net", "none")
    val nameArgs  = Seq("--name", name)

    val killCommand = Seq(engine.command, "kill", name)
    val fullCommand =
      Seq(engine.command, "run") ++ Seq("--rm") ++ nameArgs ++ netArgs ++ engine.additionalArgs ++ mountArgs ++ command

    for
      out <- ZIO.succeed(new java.io.ByteArrayOutputStream)
      _   <- ZIO.addFinalizer(ZIO.attemptBlocking(killCommand.!(ContainerEngine.noOutput)).ignore)
      _   <- ZIO.attemptBlocking((fullCommand #> out).!)
    yield out.toByteArray

object ContainerEngine:
  lazy val live: ZLayer[Any, Throwable, ContainerEngine] = ZLayer.fromZIO(
    for
      command <- getCommandBase
      _       <- Console.printLine(s"Using $command as container engine")
    yield ContainerEngine(command)
  )

  private enum Engine(
      val command: String,
      val mountSuffix: String,
      val additionalArgs: List[String]
  ):
    case Docker extends Engine("docker", "", List("--log-driver", "none"))
    case Podman extends Engine("podman", ":Z", Nil)

  private lazy val getCommandBase: Task[Engine] = for
    dockerE <- ZIO.attemptBlocking(Seq("docker", "info").!(noOutput) == 0).either
    podmanE <- ZIO.attemptBlocking(Seq("podman", "info").!(noOutput) == 0).either
    result  <- (dockerE, podmanE) match
                 case (Right(true), _)             => ZIO.succeed(Engine.Docker)
                 case (_, Right(true))             => ZIO.succeed(Engine.Podman)
                 case (Right(false), Right(false)) => ZIO.fail(new Exception("Neither docker nor podman works"))
                 case (Left(e), _)                 => ZIO.fail(e)
                 case (_, Left(e))                 => ZIO.fail(e)
  yield result

  private val noOutput = ProcessLogger(_ => ())
