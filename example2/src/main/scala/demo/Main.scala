package demo

import com.twitter.conversions.time._
import com.twitter.finagle.http.{Method, Request, RequestBuilder, Response, Status => HttpStatus}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.{Http, ListeningServer, Service}
import com.twitter.io.Buf
import com.twitter.util.{Await, Future}


object Main extends com.twitter.app.App {
  implicit val timer = DefaultTimer.getInstance

  def main(): Unit = {

    val servers: Seq[ListeningServer] = 9080 until 9081 map { port =>
      val service: Service[Request, Response] = (_: Request) => {
        println("ping")
        Future(Response(HttpStatus.Ok).content(Buf.Utf8(s"Port $port")))
          .delayed(1.second)
          .mask({
            case _ =>
              println("Connection closed")
              false
          })
      }


      Http.serve(s":$port", service)
    }

    val client = Http.client
      .withSessionQualifier.noFailFast
      .withSessionQualifier.noFailureAccrual
      .withRequestTimeout(250.millis)
      .newService("localhost:9080", "demo_client")

    def get(): Future[Any] = client(Request(Method.Get, "/"))
      .foreach(response => println(response.contentString))
      .rescue({ case e => Future.value(println(e.getMessage)) })
      .delayed(1.second)
      .ensure(get())

    get()

    Await.ready(servers.head)
  }
}
