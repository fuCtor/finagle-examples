package demo

import com.twitter.app.LoadService.Binding
import com.twitter.finagle._
import com.twitter.finagle.http.{Method, Request, Response, Status}
import com.twitter.finagle.loadbalancer.Custom
import com.twitter.finagle.util.DefaultTimer
import com.twitter.io.Buf
import com.twitter.util._
import com.twitter.conversions.time._

object Main extends com.twitter.app.App {

  implicit val timer: Timer = DefaultTimer.getInstance
  override protected[this] val loadServiceBindings: Seq[Binding[_]] = {
    Seq(new Binding(classOf[Resolver], new CustomResolver()))
  }

  //Change group number and look n console
  loadbalancer.defaultBalancerFactory(Custom.custom(group = 2))

  def main(): Unit = {

    val servers: Seq[ListeningServer] = 9080 until 9085 map { port =>
      val service: Service[Request, Response] = (_: Request) =>
        Future.value(Response(Status.Ok).content(Buf.Utf8(s"Port $port")))

      Http.serve(s":$port", service)
    }

    Resolver.eval("local!9080") match {
      case Name.Bound(n) => n.changes.respond(println)
      case _ =>
    }

    val client = Http.client.newService("local!9080")

    def get(): Future[Response] = client(Request(Method.Get, "/"))
      .foreach(response => println(response.contentString))
      .delayed(1.second)
      .flatMap(_ => get())

    get()

    Await.ready(servers.head)
  }
}
