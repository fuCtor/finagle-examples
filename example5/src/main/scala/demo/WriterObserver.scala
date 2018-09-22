package demo

import com.twitter.io.{Buf, Writer}
import com.twitter.util.Closable
import monix.execution.Ack
import monix.reactive.Observer

import scala.concurrent.Future

class WriterObserver private(writer: Writer with Closable) extends Observer[Buf] {
  @volatile private var running: Boolean = true

  override def onNext(elem: Buf): Future[Ack] = {
    writer.write(elem).onFailure(_ => running = false)
    if (running) Future.successful(Ack.Continue)
    else Future.successful(Ack.Stop)
  }

  override def onError(ex: Throwable): Unit = writer.fail(ex)

  override def onComplete(): Unit = writer.close()
}

object WriterObserver {
  def apply(writer: Writer with Closable): WriterObserver = new WriterObserver(writer)
}
