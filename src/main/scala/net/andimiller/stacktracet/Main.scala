package net.andimiller.stacktracet

import cats.effect._
import cats._
import cats.implicits._
import StackTraceT._

object Main extends IOApp {

  def hi[F[_]: Sync] = Sync[F].delay { println("hi") }

  def doThings[F[_]](implicit F: Sync[F]) = {
    for {
      _ <- F.delay { println("hello") }
      _ <- hi[F]
      a <- F.pure(2)
      b <- F.pure(2)
      _ <- F.raiseError[Unit](new Throwable("foo bar"))
    } yield a + b
  }

  override def run(args: List[String]): IO[ExitCode] = {
    doThings[StackTraceT[IO, ?]].run(List.empty).flatMap { r =>
      IO {
        println(r)
      }
    }.as(ExitCode.Success)
  }
}
