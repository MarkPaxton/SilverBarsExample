package repositories

import javax.inject.{Inject, _}

import models.Order

import scala.concurrent.Future


trait OrderRepository {
  def register(order: Order): Future[Long]

  def cancel(id: Long): Future[Unit]

  def sellQuery(): Future[Seq[Order]]

  def buyQuery(): Future[Seq[Order]]
}

@Singleton
class OrderRepositoryInMemory @Inject()() extends OrderRepository {
  private val ids = Iterator.from(1)
  private var openOrders = Map[Long, Order]()

  override def register(order: Order): Future[Long] = {
    order match {
      case Order(None, _, _, _, op: String) if (Order.validTypes.contains(op)) => {
        val id = ids.next()
        openOrders = openOrders.updated(id, order.copy(id = Some(id)))
        Future.successful(id)
      }
      case _ => Future.failed(new Exception("Invalid order"))
    }
  }

  override def cancel(id: Long): Future[Unit] = {
    openOrders.get(id) match {
      case Some(order: Order) => {
        openOrders = openOrders - id
        Future.successful(() => {})
      }
      case _ => Future.failed(new Exception("Not an open order"))
    }
  }

  private def orderQuery(op: String) = Future.successful(openOrders.filter(_._2.orderType == op).map(_._2).toSeq)

  override def sellQuery(): Future[Seq[Order]] = orderQuery("SELL")

  override def buyQuery(): Future[Seq[Order]] = orderQuery("BUY")

}
