package models

import play.api.libs.json.Json

case class Order(id:Option[Long], userId:Long, amount:BigDecimal, price: BigDecimal, orderType:String)

object Order {
  implicit val format = Json.format[Order]

  val validTypes = Seq("BUY", "SELL")
}