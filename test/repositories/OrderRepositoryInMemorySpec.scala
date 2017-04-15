package repositories

import models.Order
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerTest

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class OrderRepositoryInMemorySpec extends PlaySpec with GuiceOneAppPerTest with ScalaFutures {

  "OrderRepositoryInMemory.create" should {

    "register first order with no ID as order 1" in {
      val order = Order(None, 1, 1, 10, "BUY")
      val repository = app.injector.instanceOf(classOf[OrderRepositoryInMemory])

      ScalaFutures.whenReady(repository.register(order)) { r =>
        r mustBe 1
      }
    }


    "register first and second orders with no ID as order 1 and 2" in {
      val order1 = Order(None, 1, 1, 10, "BUY")
      val order2 = Order(None, 2, 1, 10, "BUY")
      val repository = app.injector.instanceOf(classOf[OrderRepositoryInMemory])

      val f = Future.sequence(Seq(repository.register(order1), repository.register(order2)))
      ScalaFutures.whenReady(f) { r =>
        r mustBe Seq(1, 2)
      }
    }

    "fail to register an order with and ID" in {
      val order = Order(Some(1), 1, 1, 10, "BUY")
      val repository = app.injector.instanceOf(classOf[OrderRepositoryInMemory])

      val f = repository.register(order)
      ScalaFutures.whenReady(f.failed) { e =>
        e mustBe an[Exception]
      }
    }

    "fail to register an order with an invalid type" in {
      val order = Order(None, 1, 1, 10, "HOARD")
      val repository = app.injector.instanceOf(classOf[OrderRepositoryInMemory])

      val f = repository.register(order)
      ScalaFutures.whenReady(f.failed) { e =>
        e mustBe an[Exception]
      }
    }

    "find a registered sell order" in {
      val order = Order(None, 1, 1, 10, "SELL")
      val repository = app.injector.instanceOf(classOf[OrderRepositoryInMemory])

      val f = repository.register(order).flatMap({ id =>
        repository.sellQuery().map({ sells =>
          (order.copy(id = Some(id)), sells)
        })
      })

      ScalaFutures.whenReady(f) { r =>
        r._2 mustBe Seq(r._1)
      }
    }

    "find a registered buy order" in {
      val order = Order(None, 1, 1, 10, "BUY")
      val repository = app.injector.instanceOf(classOf[OrderRepositoryInMemory])

      val f = repository.register(order).flatMap({ id =>
        repository.buyQuery().map({ sells =>
          (order.copy(id = Some(id)), sells)
        })
      })

      ScalaFutures.whenReady(f) { r =>
        r._2 mustBe Seq(r._1)
      }
    }

    "find all registered sell orders" in {
      val orders = Seq(
        Order(None, 1, 1, 10, "SELL"),
        Order(None, 1, 1, 10, "BUY"),
        Order(None, 1, 1, 10, "SELL"),
        Order(None, 1, 1, 10, "BUY"),
        Order(None, 1, 1, 10, "SELL")
      )

      val repository = app.injector.instanceOf(classOf[OrderRepositoryInMemory])

      val f = Future.sequence(orders.map(order =>
        repository.register(order).map(id => order.copy(id = Some(id))))
      ).flatMap(expectedOrders =>
        repository.buyQuery().map(buyOrders => (expectedOrders.filter(_.orderType=="BUY"), buyOrders))
      )

      ScalaFutures.whenReady(f) { r =>
        r._2.sortBy(_.id) mustBe r._1.sortBy(_.id)
      }
    }

    "cancel a registered order" in {
      val order = Order(None, 1, 1, 10, "BUY")
      val repository = app.injector.instanceOf(classOf[OrderRepositoryInMemory])

      val f = repository.register(order)
        .flatMap(id => repository.cancel(id)
          .flatMap(_ => repository.buyQuery().map(buyOrders => (id, buyOrders.map(_.id.get))))
      )
      ScalaFutures.whenReady(f) { r =>
        r._2.contains(r._1) mustBe false
      }
    }

    "fail to cancel a cancelled order" in {
      val order = Order(None, 1, 1, 10, "BUY")
      val repository = app.injector.instanceOf(classOf[OrderRepositoryInMemory])

      val f = repository.register(order)
        .flatMap(id => repository.cancel(id)
          .flatMap(_ => repository.cancel(id))
        )
      ScalaFutures.whenReady(f.failed) { e =>
        e mustBe an [Exception]
      }
    }

    "fail to cancel an non-existing order" in {
      val orderId = 100
      val repository = app.injector.instanceOf(classOf[OrderRepositoryInMemory])

      ScalaFutures.whenReady(repository.cancel(orderId).failed) { e =>
        e mustBe an [Exception]
      }
    }


    "not interfere with other orders when cancelling an order" in {
      val orders = Seq(
        Order(None, 1, 1, 10, "SELL"),
        Order(None, 1, 1, 10, "BUY"),
        Order(None, 1, 1, 10, "SELL"),
        Order(None, 1, 1, 10, "BUY"),
        Order(None, 1, 1, 10, "SELL")
      )

      val repository = app.injector.instanceOf(classOf[OrderRepositoryInMemory])

      val f = for {
        expectedOrders <- Future.sequence(orders.map(order => repository.register(order).map(id => order.copy(id = Some(id)))))
        cancelledId = expectedOrders.last.id.get
        _ <- repository.cancel(cancelledId)
        registeredOrders <- Future.sequence(Seq(repository.buyQuery(), repository.sellQuery()))
      } yield (expectedOrders.filterNot(_.id.get == cancelledId), registeredOrders.flatten)

      ScalaFutures.whenReady(f) { r =>
        r._2.sortBy(_.id) mustBe r._1.sortBy(_.id)
      }
    }

  }
}
