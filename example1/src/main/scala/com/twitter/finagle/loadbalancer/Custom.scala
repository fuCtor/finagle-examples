package com.twitter.finagle.loadbalancer

import com.twitter.finagle.loadbalancer.roundrobin.RoundRobinBalancer
import com.twitter.finagle._
import com.twitter.finagle.loadbalancer.custom.CustomBalancer
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.{Activity, Future, Time}

object Custom {

  private def newScopedBal[Req, Rep](
                                      label: String,
                                      sr: StatsReceiver,
                                      lbType: String,
                                      bal: ServiceFactory[Req, Rep]
                                    ): ServiceFactory[Req, Rep] = {
    bal match {
      case balancer: Balancer[Req, Rep] => balancer.register(label)
      case _ => ()
    }

    new ServiceFactoryProxy(bal) {
      private[this] val typeGauge = sr.scope("algorithm").addGauge(lbType)(1)
      override def close(when: Time): Future[Unit] = {
        typeGauge.remove()
        super.close(when)
      }
    }
  }

  def custom(
              group: Int = 0
            ): LoadBalancerFactory = new LoadBalancerFactory {
    override def toString: String = "custom"
    def newBalancer[Req, Rep](
                               endpoints: Activity[IndexedSeq[EndpointFactory[Req, Rep]]],
                               exc: NoBrokersAvailableException,
                               params: Stack.Params
                             ): ServiceFactory[Req, Rep] = {
      val sr = params[param.Stats].statsReceiver
      val balancer = new CustomBalancer(endpoints, sr, exc, 5, group)
      newScopedBal(params[param.Label].label, sr, "custom", balancer)
    }
  }
}