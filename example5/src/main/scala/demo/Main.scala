package demo

import com.twitter.finagle._
import com.twitter.util.Await


object Main extends com.twitter.app.App {
  def main(): Unit = {
    val server: ListeningServer = Http.serve( ":9080", DemoService.router)

    closeOnExit(server)
    Await.ready(server)
  }
}