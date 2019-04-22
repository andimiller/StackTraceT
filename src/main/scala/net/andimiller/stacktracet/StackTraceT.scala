package net.andimiller.stacktracet

import cats._
import cats.data.{Kleisli, ReaderT, StateT, WriterT}
import cats.implicits._
import cats.effect._

import scala.collection.JavaConverters._

object StackTraceT {

  type StackTraceT[F[_], T] = StateT[F, List[StackTraceElement], T]

  def getStackTrace(): Option[StackTraceElement] =
    new Throwable().getStackTrace.toList.find { t =>
      !t.getClassName.startsWith("cats.") &&
        !t.getClassName.startsWith("scala.") &&
        !t.getClassName.startsWith("net.andimiller.stacktracet.StackTraceT")
    }

  implicit def stackTraceTSync[F[_]](implicit F: Sync[F]) = new Sync[StackTraceT[F, ?]] with StackSafeMonad[StackTraceT[F, ?]] {
    override def suspend[A](thunk: => StackTraceT[F, A]): StackTraceT[F, A] = {
      val stack = getStackTrace().toList
      thunk.contramap(s => stack ++ s)
    }
    override def bracketCase[A, B](acquire: StackTraceT[F, A])(use: A => StackTraceT[F, B])(release: (A, ExitCase[Throwable]) => StackTraceT[F, Unit]): StackTraceT[F, B] =
      StateT { s =>
        F.bracketCase(acquire.run(s))(a => use(a._2).run(a._1)) { case (a, exitcase) =>
            release(a._2, exitcase).run(a._1).void
        }
      }
    override def flatMap[A, B](fa: StackTraceT[F, A])(f: A => StackTraceT[F, B]): StackTraceT[F, B] = {
      val stack = getStackTrace().toList
      fa.flatMap(f).contramap(s => stack ++ s)
    }
    override def raiseError[A](e: Throwable): StackTraceT[F, A] =
      StateT { s =>
        e.setStackTrace(s.toArray)
        F.raiseError(e)
      }
    override def handleErrorWith[A](fa: StackTraceT[F, A])(f: Throwable => StackTraceT[F, A]): StackTraceT[F, A] =
      StateT { s =>
        F.handleErrorWith(fa.run(s))(t => f(t).run(s))
      }
    override def pure[A](x: A): StackTraceT[F, A] = {
      //val stack = getStackTrace().toList
      StateT.pure[F, List[StackTraceElement], A](x) //.contramap(s => stack ++ s)
    }
  }

  implicit def stackTraceTConcurrentEffect[F[_]](implicit F: Sync[F], CE: ConcurrentEffect[F]) = new ConcurrentEffect[StackTraceT[F, ?]] {
    val S = implicitly[Sync[StackTraceT[F, ?]]]

    override def runCancelable[A](fa: StackTraceT[F, A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[CancelToken[StackTraceT[F, ?]]] = ???
    override def start[A](fa: StackTraceT[F, A]): StackTraceT[F, Fiber[StackTraceT[F, ?], A]] = ???
    override def racePair[A, B](fa: StackTraceT[F, A], fb: StackTraceT[F, B]): StackTraceT[F, Either[(A, Fiber[StackTraceT[F, ?], B]), (Fiber[StackTraceT[F, ?], A], B)]] = ???
    override def runAsync[A](fa: StackTraceT[F, A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[Unit] =
      CE.runAsync(fa.run(List.empty))(cb.compose(_.map(_._2)))
    override def async[A](k: (Either[Throwable, A] => Unit) => Unit): StackTraceT[F, A] =
      StateT.liftF(CE.async(k))
    override def asyncF[A](k: (Either[Throwable, A] => Unit) => StackTraceT[F, Unit]): StackTraceT[F, A] =
      CE.asyncF(k.andThen(_.run(List.empty)))

    // from Sync
    override def suspend[A](thunk: => StackTraceT[F, A]): StackTraceT[F, A] = S.suspend(thunk)
    override def bracketCase[A, B](acquire: StackTraceT[F, A])(use: A => StackTraceT[F, B])(release: (A, ExitCase[Throwable]) => StackTraceT[F, Unit]): StackTraceT[F, B] = S.bracketCase(acquire)(use)(release)
    override def flatMap[A, B](fa: StackTraceT[F, A])(f: A => StackTraceT[F, B]): StackTraceT[F, B] = S.flatMap(fa)(f)
    override def tailRecM[A, B](a: A)(f: A => StackTraceT[F, Either[A, B]]): StackTraceT[F, B] = S.tailRecM(a)(f)
    override def raiseError[A](e: Throwable): StackTraceT[F, A] = S.raiseError(e)
    override def handleErrorWith[A](fa: StackTraceT[F, A])(f: Throwable => StackTraceT[F, A]): StackTraceT[F, A] = S.handleErrorWith(fa)(f)
    override def pure[A](x: A): StackTraceT[F, A] = S.pure(x)
  }

}
