package models

import play.api.libs.json.Json

case class Order(id:Option[Long], userId:Long, amount:BigDecimal, orderType:String)

object Order {
  implicit val format = Json.format[Order]
}