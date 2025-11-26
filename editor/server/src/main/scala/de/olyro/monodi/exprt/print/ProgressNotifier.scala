package de.olyro.monodi.exprt.print

import zio.*

trait ProgressNotifier:
  def notify(progress: Progress): UIO[Unit]

object ProgressNotifier:
  val dummy: ZLayer[Any, Nothing, ProgressNotifier] =
    ZLayer.succeed(new ProgressNotifier {
      def notify(progress: Progress): UIO[Unit] = ZIO.unit
    })

  def fromCallback(callback: Progress => UIO[Unit]): ZLayer[Any, Nothing, ProgressNotifier] =
    ZLayer.succeed(new ProgressNotifier {
      def notify(progress: Progress): UIO[Unit] = callback(progress)
    })

  def toConsole: ZLayer[Any, Nothing, ProgressNotifier] =
    ZLayer.succeed(new ProgressNotifier {
      def notify(progress: Progress): UIO[Unit] = Console.printLine(progress.toString).orDie
    })

  def notify(progress: Progress): URIO[ProgressNotifier, Unit] =
    ZIO.serviceWithZIO[ProgressNotifier](_.notify(progress))
