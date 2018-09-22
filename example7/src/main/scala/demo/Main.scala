package demo

import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.service.KetamaShardingServiceBuilder
import com.twitter.finagle.{Http, Service, SimpleFilter}
import com.twitter.io.Buf
import com.twitter.util.{Await, Future}


object Main extends com.twitter.app.App {
  val shardField: Request.Schema.Field[String] = Request.Schema.newField[String]("")

  def main(): Unit = {

    val services = (0 to 9).map(_.toString).map(c =>
      c -> Service.mk((_: Request) =>
        Future.value(Response(Status.Ok).content(Buf.Utf8(c)))
      )
    )

    def sharder(request: Request): Option[Long] = Some(request.ctx[String](shardField).hashCode.toLong)

    val factory = KetamaShardingServiceBuilder()
      .nodes(services)
      .withHash(sharder)
      .buildFactory()

    val filter: SimpleFilter[Request, Response] = (request: Request, service: Service[Request, Response]) => {
      request.params.get("s") match {
        case Some(s) if s.nonEmpty =>
          request.ctx.update(shardField, s)
          service(request)
        case _ => Future.value(Response(Status.BadRequest))
      }
    }

    val server = Http.server.serve(":9080", filter.andThen(factory))

    Await.ready(server)
  }
}
