package demo

import com.twitter.concurrent.AsyncStream
import com.twitter.conversions.time._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.finagle.http.path._
import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.util.DefaultTimer
import com.twitter.io.Buf
import com.twitter.util.Future

import scala.util.parsing.json.JSONObject

object DemoService {
  import DefaultTimer.Implicit

  def userServiceJson(id: Int): Service[Request, Response] = (req: Request) => {
    val rep = Response(Version.Http11, Status.Ok)
    val o = JSONObject(Map("id" -> id, "name" -> "John Smith"))
    rep.setContentTypeJson()
    rep.contentString = o.toString

    Future(rep)
  }

  def userServicePlain(id: Int): Service[Request, Response] = (req: Request) => {
    val rep = Response(Version.Http11, Status.Ok)
    rep.contentString = s"$id => John Smith"

    Future(rep)
  }

  def echoService(message: String): Service[Request, Response] = (req: Request) => {
    val rep = Response(req.version, Status.Ok)
    rep.setContentString(message)
    Future(rep)
  }

  def streamService(stream: AsyncStream[String]): Service[Request, Response] = (req: Request) => {
    val rep = Response(req.version, Status.Ok)
    rep.setChunked(true)
    val writer = rep.writer
    stream.foreachF({ s =>
      println(s)
      writer.write(Buf.Utf8(s).concat(newLine))
    }).ensure(writer.close())
    Future(rep)
  }

  val newLine: Buf = Buf.Utf8("\n")

  def ping(sec: Int): AsyncStream[String] = AsyncStream.fromFuture(Future("ping").delayed(sec.second)).concat(ping(sec))

  def router: Service[Request, Response] = RoutingService.byMethodAndPathObject[Request]({
    case Method.Get -> Root / "user" / Integer(id) ~ "json" => userServiceJson(id)
    case Method.Get -> Root / "user" / Integer(id) => userServicePlain(id)
    case _ -> Root / "echo"/ message => echoService(message)
    case Method.Get -> Root / "ping" / Integer(sec) => streamService(ping(sec))
    case _ -> Root / (prefix @ "chat") / _  => PubSubService.router(Root / prefix)
  })

}
