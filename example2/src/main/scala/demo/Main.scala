package demo

import com.twitter.finagle.{Http, ListeningServer, Service}
import com.twitter.finagle.http.{Method, Request, Response, Status => HttpStatus}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.io.Buf
import com.twitter.util.{Await, Duration, Future}


object Main extends com.twitter.app.App {
  implicit val timer = DefaultTimer.getInstance

  def main(): Unit = {

    val servers: Seq[ListeningServer] = 8080 until 8081 map { port =>
      val service: Service[Request, Response] = (_: Request) => {
        println("ping")
        Future(Response(HttpStatus.Ok).content(Buf.Utf8(s"Port $port")))
          .delayed(Duration.fromSeconds(1))
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
      .withRequestTimeout(Duration.fromMilliseconds(250))
      .newService("localhost:8080", "demo_client")

    def get(): Future[Any] = client(Request(Method.Get, "/")).foreach({ response =>
      println(response.contentString)
    }).rescue({ case _ => Future.Unit })
      .delayed(Duration.fromSeconds(1)).ensure(get())

    get()

    Await.ready(servers.head)
  }
}
