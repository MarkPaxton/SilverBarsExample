package controllers

import akka.stream.Materializer
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.http.HeaderNames
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test._
import repositories.OrderRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class SilverBarsControllerSpec extends PlaySpec with GuiceOneAppPerTest with BeforeAndAfterEach{

  "SilverBarsController" should {
    "register an order and return it's id" in {
      implicit val materializer = app.injector.instanceOf(classOf[Materializer])
      val controller = app.injector.instanceOf(classOf[SilverBarsController])
      val registerResult = controller.register().apply(FakeRequest()
        .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
        .withBody[JsValue](Json.obj(
        "userId" -> 1,
        "amount" -> 1,
        "price" -> 1,
        "orderType" -> "BUY"
      )))

      status(registerResult) mustBe CREATED
      contentAsString(registerResult) mustBe ("1")
    }

    "register 2 orders and return ids 1 and 2" in {
      implicit val materializer = app.injector.instanceOf(classOf[Materializer])
      val controller = app.injector.instanceOf(classOf[SilverBarsController])
      val registerResult1 = controller.register().apply(FakeRequest()
        .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
        .withBody[JsValue](Json.obj(
        "userId" -> 1,
        "amount" -> 1,
        "price" -> 1,
        "orderType" -> "BUY")
      ))
      val registerResult2 = controller.register().apply(FakeRequest()
        .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
        .withBody[JsValue](Json.obj(
        "userId" -> 1,
        "amount" -> 1,
        "price" -> 1,
        "orderType" -> "SELL")
      ))

      status(registerResult1) mustBe CREATED
      status(registerResult2) mustBe CREATED
      contentAsString(registerResult1) mustBe ("1")
      contentAsString(registerResult2) mustBe ("2")
    }

    "fail to register an order with existing id and return http 500" in {
      implicit val materializer = app.injector.instanceOf(classOf[Materializer])
      val controller = app.injector.instanceOf(classOf[SilverBarsController])
      val registerResult = controller.register().apply(FakeRequest()
        .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
        .withBody[JsValue](Json.obj(
        "id" -> 100,
        "userId" -> 1,
        "amount" -> 1,
        "price" -> 1,
        "orderType" -> "BUY"
      )))

      status(registerResult) mustBe BAD_REQUEST
    }

    "fail to create an order with malformed json and return http 500" in {
      implicit val materializer = app.injector.instanceOf(classOf[Materializer])
      val controller = app.injector.instanceOf(classOf[SilverBarsController])
      val registerResult = controller.register().apply(FakeRequest()
        .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
        .withBody("{I am not an order}")
      ).run()

      status(registerResult) mustBe BAD_REQUEST
    }

    "fail to cancel an order that does not exist and return http 500" in {
      implicit val materializer = app.injector.instanceOf(classOf[Materializer])
      val controller = app.injector.instanceOf(classOf[SilverBarsController])
      val cancelResult = controller.cancel(9999).apply(FakeRequest())
      status(cancelResult) mustBe BAD_REQUEST
    }

    "register an order and cancel it" in {
      implicit val materializer = app.injector.instanceOf(classOf[Materializer])
      val controller = app.injector.instanceOf(classOf[SilverBarsController])
      val repoToCheck = app.injector.instanceOf(classOf[OrderRepository])

      val createResult = controller.register().apply(FakeRequest()
        .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
        .withBody[JsValue](Json.obj(
        "userId" -> 1,
        "amount" -> 1,
        "price" -> 1,
        "orderType" -> "BUY"
      )))
      val createdId = contentAsString(createResult).toLong

      val result = for {
        cancelResult <- controller.cancel(createdId).apply(FakeRequest())
        buyResult <- repoToCheck.buyQuery()
      } yield (cancelResult.header.status, buyResult)

      ScalaFutures.whenReady(result) { r =>
        r._1 mustBe NO_CONTENT
        r._2.isEmpty mustBe true
      }
    }


    "register 2 orders and cancel the correct one" in {
      implicit val materializer = app.injector.instanceOf(classOf[Materializer])
      val controller = app.injector.instanceOf(classOf[SilverBarsController])
      val repoToCheck = app.injector.instanceOf(classOf[OrderRepository])

      val create1Result = controller.register().apply(FakeRequest()
        .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
        .withBody[JsValue](Json.obj(
        "userId" -> 1,
        "amount" -> 1,
        "price" -> 1,
        "orderType" -> "BUY"
      )))
      val create2Result = controller.register().apply(FakeRequest()
        .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
        .withBody[JsValue](Json.obj(
        "userId" -> 1,
        "amount" -> 1,
        "price" -> 1,
        "orderType" -> "SELL"
      )))

      val create1Id = contentAsString(create1Result).toLong
      val create2Id = contentAsString(create2Result).toLong

      val result = for {
        cancelResult <- controller.cancel(create1Id).apply(FakeRequest())
        sellResult <- repoToCheck.sellQuery()
        buyResult <- repoToCheck.buyQuery()
      } yield (cancelResult.header.status, buyResult, sellResult)

      status(create1Result) mustBe CREATED
      status(create2Result) mustBe CREATED

      ScalaFutures.whenReady(result) { r =>
        r._1 mustBe NO_CONTENT
        r._2.isEmpty mustBe true
        r._3.find(_.id == Some(create2Id)).isDefined mustBe true
      }
    }

    "return dashboard page with no orders" in {
      val controller = app.injector.instanceOf(classOf[SilverBarsController])
      val dashboard = controller.dashboardView().apply(FakeRequest())

      status(dashboard) mustBe OK
      contentType(dashboard) mustBe Some("application/json")
    }

    "return dashboard page with simple buy and sell orders" in {
      val controller = app.injector.instanceOf(classOf[SilverBarsController])

      val testData = Seq(Json.obj(
        "userId" -> 1,
        "amount" -> 1,
        "price" -> 10,
        "orderType" -> "BUY"
      ), Json.obj(
        "userId" -> 1,
        "amount" -> 10,
        "price" -> 1,
        "orderType" -> "SELL"
      ))

      val orders = Future.sequence(testData.map( orderJson => {
        controller.register().apply(FakeRequest().withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
          .withBody[JsValue](orderJson))
      }))
      val dashboard = orders.flatMap(_ => controller.dashboardView().apply(FakeRequest()))

      ScalaFutures.whenReady(orders) { orderResults =>
        orderResults.forall(_.header.status === CREATED) mustBe true
      }
      status(dashboard) mustBe OK
      contentType(dashboard) mustBe Some("application/json")
      val data = contentAsJson(dashboard)
      val buys = (data \ "buys").as[JsArray]
      val sells = (data \ "sells").as[JsArray]
      buys.value.length mustBe 1
      sells.value.length mustBe 1

      buys.value.head.as[JsArray].value.head.as[BigDecimal] mustBe 10
      buys.value.head.as[JsArray].value.last.as[BigDecimal] mustBe 1
      sells.value.head.as[JsArray].value.head.as[BigDecimal] mustBe 1
      sells.value.head.as[JsArray].value.last.as[BigDecimal] mustBe 10
    }


    "return dashboard page with combined buy and sell orders and orders at difference prices in the right order" in {
      val controller = app.injector.instanceOf(classOf[SilverBarsController])

      val testData = Seq(Json.obj(
        "userId" -> 1,
        "amount" -> 6,
        "price" -> 10,
        "orderType" -> "BUY"
      ), Json.obj(
        "userId" -> 2,
        "amount" -> 10,
        "price" -> 10,
        "orderType" -> "BUY"
      ),Json.obj(
        "userId" -> 3,
        "amount" -> 5,
        "price" -> 105,
        "orderType" -> "BUY"
      ), Json.obj(
        "userId" -> 4,
        "amount" -> 4,
        "price" -> 104,
        "orderType" -> "BUY"
      ),Json.obj(
        "userId" -> 5,
        "amount" -> 6,
        "price" -> 11,
        "orderType" -> "BUY"
      ), Json.obj(
        "userId" -> 6,
        "amount" -> 10,
        "price" -> 12,
        "orderType" -> "BUY"
      ),Json.obj(
        "userId" -> 7,
        "amount" -> 5,
        "price" -> 1000,
        "orderType" -> "SELL"
      ), Json.obj(
        "userId" -> 8,
        "amount" -> 50,
        "price" -> 1000,
        "orderType" -> "SELL"
      ), Json.obj(
        "userId" -> 9,
        "amount" -> 80,
        "price" -> 1000,
        "orderType" -> "SELL"
      ), Json.obj(
        "userId" -> 10,
        "amount" -> 4,
        "price" -> 2000,
        "orderType" -> "SELL"
      ), Json.obj(
        "userId" -> 11,
        "amount" -> 40,
        "price" -> 1900,
        "orderType" -> "SELL"
      ), Json.obj(
        "userId" -> 12,
        "amount" -> 4,
        "price" -> 104,
        "orderType" -> "SELL"
      ))

      val orders = Future.sequence(testData.map( orderJson => {
        controller.register().apply(FakeRequest().withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
          .withBody[JsValue](orderJson))
      }))
      val dashboard = orders.flatMap(_ => controller.dashboardView().apply(FakeRequest()))


      ScalaFutures.whenReady(orders) { orderResults =>
        orderResults.forall(_.header.status === CREATED) mustBe true
      }
      status(dashboard) mustBe OK
      contentType(dashboard) mustBe Some("application/json")
      val data = contentAsJson(dashboard)

      val buys = (data \ "buys").as[JsArray]
      val sells = (data \ "sells").as[JsArray]

      buys.value.length mustBe 5
      sells.value.length mustBe 4

      buys.value(0).as[JsArray].value.head.as[BigDecimal] mustBe 105
      buys.value(0).as[JsArray].value.last.as[BigDecimal] mustBe 5

      buys.value(1).as[JsArray].value.head.as[BigDecimal] mustBe 104
      buys.value(1).as[JsArray].value.last.as[BigDecimal] mustBe 4

      buys.value(2).as[JsArray].value.head.as[BigDecimal] mustBe 12
      buys.value(2).as[JsArray].value.last.as[BigDecimal] mustBe 10


      buys.value(3).as[JsArray].value.head.as[BigDecimal] mustBe 11
      buys.value(3).as[JsArray].value.last.as[BigDecimal] mustBe 6

      buys.value(4).as[JsArray].value.head.as[BigDecimal] mustBe 10
      buys.value(4).as[JsArray].value.last.as[BigDecimal] mustBe 16

      sells.value(0).as[JsArray].value.head.as[BigDecimal] mustBe 104
      sells.value(0).as[JsArray].value.last.as[BigDecimal] mustBe 4

      sells.value(1).as[JsArray].value.head.as[BigDecimal] mustBe 1000
      sells.value(1).as[JsArray].value.last.as[BigDecimal] mustBe 135

      sells.value(2).as[JsArray].value.head.as[BigDecimal] mustBe 1900
      sells.value(2).as[JsArray].value.last.as[BigDecimal] mustBe 40

      sells.value(3).as[JsArray].value.head.as[BigDecimal] mustBe 2000
      sells.value(3).as[JsArray].value.last.as[BigDecimal] mustBe 4



    }


    "return the dashboard data from the router" in {
      // Need to specify Host header to get through AllowedHostsFilter
      val request = FakeRequest(GET, "/").withHeaders("Host" -> "localhost")
      val dashboard = route(app, request).get

      status(dashboard) mustBe OK
      contentType(dashboard) mustBe Some("application/json")
    }
  }
}
