package demo

import java.net.InetSocketAddress

import com.twitter.finagle.{Addr, Address, Resolver}
import com.twitter.util._

class CustomResolver(size: Int = 1)(implicit timer: Timer) extends Resolver {
  override val scheme: String = "local"

  override def bind(arg: String): Var[Addr] = {
    val addr = Var[Addr](Addr.Pending)

    Future.Unit.delayed(Duration.fromSeconds(1)).ensure({
      val basePort = arg.toInt
      val addresses: Set[Address] = (0 until size map { i =>
        Address.Inet(new InetSocketAddress("localhost", basePort + i), Map("group" -> i))
      }).toSet
      addr.update(Addr.Bound(addresses))
    })

    addr
  }

}