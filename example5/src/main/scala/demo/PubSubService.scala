package demo

import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.finagle.http.path._
import com.twitter.finagle.http.service.RoutingService
import com.twitter.io.Buf
import com.twitter.util.Future
import monix.execution.Scheduler
import monix.reactive.MulticastStrategy
import monix.reactive.subjects.ConcurrentSubject

object PubSubService {
  implicit val scheduler: Scheduler = Scheduler.fixedPool("task", 4)
  val newLine = Buf.Utf8("\n")

  private val subject = ConcurrentSubject[String](MulticastStrategy.publish)

  def sub(): Service[Request, Response] = (req: Request) => {
    val rep = Response(req.version, Status.Ok)
    rep.setContentType("text/plain")
    rep.setChunked(true)
    subject
      .map(Buf.Utf8(_).concat(newLine))
      .subscribe(WriterObserver(rep.writer))
    Future(rep)
  }

  def pub(): Service[Request, Response] = (req: Request) => {
    subject.onNext(req.contentString)
    Future(Response(req.version, Status.Accepted))
  }

  def complete(): Service[Request, Response] = (req: Request) => {
    subject.onComplete()
    Future(Response(req.version, Status.Accepted))
  }

  def router(prefix: Path): Service[Request, Response] = RoutingService.byMethodAndPathObject[Request] {
    case Method.Post -> `prefix` / "pub" => pub()
    case Method.Get -> `prefix` / "sub" => sub()
    case Method.Delete -> `prefix` / "close" => complete()
  }

}
