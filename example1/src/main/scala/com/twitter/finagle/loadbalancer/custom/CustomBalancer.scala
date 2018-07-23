package com.twitter.finagle.loadbalancer.custom

import com.twitter.finagle._
import com.twitter.finagle.loadbalancer._
import com.twitter.finagle.stats.{Counter, StatsReceiver}
import com.twitter.util.{Activity, Future, Time}

private[loadbalancer] final class CustomBalancer[Req, Rep](
                                          protected val endpoints: Activity[IndexedSeq[EndpointFactory[Req, Rep]]],
                                          protected val statsReceiver: StatsReceiver,
                                          protected val emptyException: NoBrokersAvailableException,
                                          protected val maxEffort: Int = 5,
                                          protected val group: Int = 0
                                        ) extends ServiceFactory[Req, Rep]
  with Balancer[Req, Rep]
  with Updating[Req, Rep] {

  protected class Node(val factory: EndpointFactory[Req, Rep])
    extends ServiceFactoryProxy[Req, Rep](factory)
      with NodeT[Req, Rep] {
    def load: Double = 0.0
    def pending: Int = 0

    def group: Option[Int] = factory.address match {
      case Address.Inet(_, metadata) => metadata.get("group").flatMap({
        case x: Int => Some(x)
        case _ => None
      })
      case _ => None
    }

    override def close(deadline: Time): Future[Unit] = factory.close(deadline)
    override def apply(conn: ClientConnection): Future[Service[Req, Rep]] = factory(conn)
  }

  protected class Distributor(vector: Vector[Node]) extends DistributorT[Node](vector) {
    type This = Distributor

    @volatile
    protected[this] var sawDown = false

    private[this] val (up: Vector[Node], down: Vector[Node]) = vector.partition(_.isAvailable)

    protected[this] val selections: Vector[Node] = if (up.isEmpty) down else up

    def pick(): Node = {
      if (vector.isEmpty)
        return failingNode(emptyException)

      selections.find(_.group.contains(group)) match {
        case Some(node) if node.status == Status.Open => node
        case Some(node) =>
          sawDown
          node
        case _ => failingNode(emptyException)
      }
    }

    def rebuild(): This = new Distributor(vector)
    def rebuild(vector: Vector[Node]): This = new Distributor(vector)

    def needsRebuild: Boolean = {
      sawDown || (down.nonEmpty && down.exists(_.isAvailable))
    }
  }

  override protected def newNode(factory: EndpointFactory[Req, Rep]): Node = new Node(factory)

  override protected def failingNode(cause: Throwable): Node = new Node(new FailingEndpointFactory(cause))

  override protected def initDistributor(): Distributor = new Distributor(Vector.empty)

  override protected[this] def maxEffortExhausted: Counter = statsReceiver.counter("max_effort_exhausted")

  override def additionalMetadata: Map[String, Any] = Map.empty
}

