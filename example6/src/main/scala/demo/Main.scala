package demo

import com.twitter.finagle.{Serial, Service}
import com.twitter.util._

object Main extends com.twitter.app.App {

  def main(): Unit = {

    val service = Service.mk[String, String](s =>
      Future.value(s"<< $s\n")
    )
    val server = Serial.server[String, String]().serve(":9080", service)

    Await.ready(server)
  }
}
