package demo

import com.twitter.app.LoadService.Binding
import com.twitter.conversions.time._
import com.twitter.finagle.http.{Method, Request, RequestBuilder, Response, Status => HttpStatus}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Http, ListeningServer, Resolver, Service}
import com.twitter.io.Buf
import com.twitter.util.{Await, Future}

object Config {
  val hostsCount = 1
  val poolSize = 2
  val parallelRequests = 2
}

object Main extends com.twitter.app.App {
  implicit val timer = DefaultTimer.getInstance

  override protected[this] val loadServiceBindings: Seq[Binding[_]] = {
    Seq(new Binding(classOf[Resolver], new CustomResolver(Config.hostsCount)))
  }

  def isolatedClient(marker: String) = {
    val client = Http.client
      .withSessionQualifier.noFailFast
      .withSessionQualifier.noFailureAccrual
      .withSessionPool.maxSize(Config.poolSize)
      .withSessionPool.maxWaiters(0)
      .newService("local!9080", "demo_client")


    def singleGet() = client(Request(Method.Get, marker))
      .foreach(resp => println(resp.contentString))

    def get(): Future[Any] = Future.collect(Seq.fill(Config.parallelRequests)(singleGet()))
      .rescue({ case e => Future.value(println(e)) })
      .delayed(1.second).ensure(get())

    get()
  }

  def main(): Unit = {

    val servers: Seq[ListeningServer] = 0 until Config.hostsCount map { i =>
      val port = 9080 + i
      val service: Service[Request, Response] = (req: Request) =>
        Future(Response(HttpStatus.Ok).content(Buf.Utf8(s"${req.path} Port $port")))
          .delayed(1.second)

      Http.serve(s":$port", service)
    }

    isolatedClient("test1")
    isolatedClient("test2")

    Await.ready(servers.head)
  }
}
