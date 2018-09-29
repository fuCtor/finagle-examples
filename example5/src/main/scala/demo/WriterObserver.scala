package demo

import com.twitter.io.{Buf, Writer}
import com.twitter.util.Closable
import monix.execution.Ack
import monix.reactive.Observer

import scala.concurrent.{Future, Promise}

class WriterObserver private(writer: Writer with Closable) extends Observer[Buf] {
  override def onNext(elem: Buf): Future[Ack] = {
    val promise = Promise[Ack]
    writer.write(elem)
      .onFailure(_ => promise.success(Ack.Stop))
      .onSuccess(_ => promise.success(Ack.Continue))
    promise.future
  }

  override def onError(ex: Throwable): Unit = writer.fail(ex)

  override def onComplete(): Unit = writer.close()
}

object WriterObserver {
  def apply(writer: Writer with Closable): WriterObserver = new WriterObserver(writer)
}
