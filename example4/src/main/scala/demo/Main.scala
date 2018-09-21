package demo

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

import com.twitter.app.LoadService.Binding
import com.twitter.conversions.time._
import com.twitter.finagle._
import com.twitter.finagle.http.service.HttpResponseClassifier
import com.twitter.finagle.http.{Method, Request, Response, Status}
import com.twitter.finagle.loadbalancer.Balancers
import com.twitter.finagle.util.{DefaultTimer, Rng}
import com.twitter.util.{Future, Timer}

object Config {
  val hostsCount = 5
  val basePort = 9080
  val reqPeriod = 5.millis
  val batch = 100
  val failProbability = 0.00
  val concurrency = 5

  val rnd = new java.util.Random(0)
  val rng = Rng(rnd)

  //  val balancer = Balancers.roundRobin()
  val balancer = Balancers.aperture(smoothWin = 5.second, rng = rng)
  //  val balancer = Balancers.aperturePeakEwma(smoothWin = 5.second)
  //  val balancer = Balancers.heap(rnd)
  //  val balancer = Balancers.p2c(rng = rng)
  //  val balancer = Balancers.p2cPeakEwma(decayTime = 5.second, rng = rng)
}

object Main extends com.twitter.app.App {

  implicit val timer: Timer = DefaultTimer.getInstance
  override protected[this] val loadServiceBindings: Seq[Binding[_]] = {
    Seq(new Binding(classOf[Resolver], new CustomResolver(Config.hostsCount)))
  }

  def main(): Unit = {
    val counters = 0.until(Config.hostsCount).map({ i =>
      i -> new AtomicLong(0)
    }).toMap

    val reqCounter = new CountDownLatch(10000)

    val servers: Seq[ListeningServer] = 0 until Config.hostsCount map { i =>
      val service: Service[Request, Response] = (_: Request) => {
        reqCounter.countDown()
        counters(i).incrementAndGet()
        val resp = if (math.random() < Config.failProbability) Response(Status.ServiceUnavailable)
        else Response(Status.NoContent)
        //        Future.value(resp).delayed(((i+1)*10).millis)
        Future.value(resp).delayed(10.millis)
      }
      val port = Config.basePort + i
      Http.serve(s":$port", service)
    }

    val client = Http
      .client
      .withResponseClassifier(HttpResponseClassifier.ServerErrorsAsFailures)
      .withSessionQualifier.successRateFailureAccrual(0.99, 5.second)
      .withLoadBalancer(Config.balancer)
      .newService(s"local!${Config.basePort}")

    def dump(): Future[Unit] =
      Future.value(println(counters.map(c => c._1 -> c._2.get()).toList.sortBy(_._1).map(_._2).mkString("\t")))
        .delayed(1.second).flatMap(_ => dump())

    def get(idx: Int): Future[Unit] =
      client(Request(Method.Get, "/")).rescue({
        case e => Future.Unit
      }).delayed(Config.reqPeriod).flatMap(_ =>
        if (reqCounter.getCount > 0) get(idx)
        else Future.Unit
      )

    0.until(Config.concurrency).foreach(get)
    dump()

    reqCounter.await()

    servers.foreach(_.close())
  }
}