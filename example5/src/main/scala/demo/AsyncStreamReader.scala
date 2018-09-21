package demo

import com.twitter.concurrent.AsyncStream
import com.twitter.io.{Buf, Reader}
import com.twitter.util.Future

class AsyncStreamReader[T : AsyncStreamReader.Codec](s: AsyncStream[T]) extends Reader {
  @volatile private var stream: Future[Option[AsyncStream[T]]] = Future.value(Some(s))
  override def read(n: Int): Future[Option[Buf]] = {
    val head: Future[Option[T]] = stream.flatMap {
      case Some(st) =>
        stream = st.tail
        st.head
      case _ => Future.None
    }

    head.map(_.map(AsyncStreamReader.Codec[T]))
  }

  override def discard(): Unit = stream = Future.None
}

object AsyncStreamReader {
  trait Codec[T] extends (T => Buf)

  object Codec {
    def apply[T : Codec]: Codec[T] = implicitly[Codec[T]]
  }

  def apply[T : Codec](s: AsyncStream[T]): AsyncStreamReader[T] = new AsyncStreamReader(s)
}
