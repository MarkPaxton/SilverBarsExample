package controllers

import javax.inject._

import models.Order
import play.api.libs.json._
import play.api.mvc._
import repositories.OrderRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class SilverBarsController @Inject()(repo: OrderRepository) extends Controller {

  def register = Action.async(parse.json) { implicit request =>
    withJsonBody[Order] { order =>
      if (Order.validTypes.contains(order.orderType)) {
        repo.register(order).map(id => Created(s"$id")).recover {
          case t: Throwable => BadRequest(t.getMessage)
          case _ => BadRequest
        }
      } else {
        Future.successful(BadRequest("OrderType not recognised"))
      }
    }
  }

  def cancel(id: Long) = Action.async { implicit request =>
    repo.cancel(id).map(id => NoContent).recover {
      case t: Throwable => BadRequest(t.getMessage)
      case _ => BadRequest
    }
  }

  private def groupAndSum(orders: Seq[Order]) = orders.groupBy(_.price).mapValues(ordersOfPrice => ordersOfPrice.map(_.amount).sum)

  def dashboardView() = Action.async {
    val eventualBuys = repo.buyQuery().map(groupAndSum(_))
    val eventualSells = repo.sellQuery().map(groupAndSum(_))
    for {
      buys <- eventualBuys
      sells <- eventualSells
      sortedBuysJs = buys.toSeq.sortBy(-_._1)
      sortedSellsJs = sells.toSeq.sortBy(_._1)
    } yield Ok(Json.obj(
      "buys" -> sortedBuysJs.map(g => Seq(JsNumber(g._1), JsNumber(g._2))),
      "sells" -> sortedSellsJs.map(g => Seq(JsNumber(g._1), JsNumber(g._2)))
    ))
  }

  def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]) = {
    request.body.validate[T] match {
      case JsSuccess(v, _) => f(v)
      case JsError(err) => Future.successful(BadRequest(err.toString))
    }
  }

}
