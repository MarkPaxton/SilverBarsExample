# SilverBarsExample
##Example Scala code, Silver Bars Ordering API

This implementation is a relatively standard Play 2.5 Application

In creating this example I made a number of assumptions:

1) __In memory order repository__ - the trait "OrderRepository" implements minimal features, just enoughh for the required functionality.
 
 * I am assuming that this would be a connector to another service, or database, so wrapped all calls in Futures to emulate this.
 * Another implementation can added and configured using the dependency injection in Module.scala
 
2) The routes have an IP filter configured to localhost
I would assume this would point to an upstream load balancer or proxy and not be exposed directly


##The API


|  Method | Url  | Description  | |
|---|---|---|---|
| GET | / | Get the dashboard data | Json object with "buys" and "sells" containing ordered arrays of [ "price", "amount" ] |
| POST | / | Post with application/json with the fields as per { "userId": Long, "amount":Decimal, "price":Double, orderType:"BUY"/"SELL" } | Status:201 (Created), Body: Created order ID|
| DELETE | /{id} | Cancels the order with the given id if it exits and returns | Status:204 (No Content) if deleted  |


##Example curl commands

curl -X GET http://localhost:9000

{"buys":[[13,7.5]],"sells":[[100,10]]}

curl -H "Content-Type: application/json" -X POST -d '{"userId":1, "amount":1, "price":100,"orderType":"SELL"}' http://localhost:9000

curl -X DELETE http://localhost:9000/1