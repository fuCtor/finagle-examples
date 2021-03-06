package demo

import java.net.InetSocketAddress

import com.twitter.finagle.{Addr, Address, Resolver}
import com.twitter.util._

class CustomResolver(implicit timer: Timer) extends Resolver {
  override val scheme: String = "local"

  override def bind(arg: String): Var[Addr] = {
    val addr = Var[Addr](Addr.Pending)

    Future.Unit.delayed(Duration.fromSeconds(1)).ensure({
      val basePort = arg.toInt
      val addresses: Set[Address] = (0 until 5 map { i =>
        Address.Inet(new InetSocketAddress(basePort + i), Map("group" -> i))
      } ).toSet
      addr.update(Addr.Bound(addresses))
    })

    addr
  }

}

