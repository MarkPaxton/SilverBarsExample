# SilverBarsExample
##Example Scala code, Silver Bars Ordering API

This implementation is a relatively standard Play 2.5 Application

In creating this example I made a number of assumptions:

1) __In memory order repository__ - the trait "OrderRepository" implements minimal features, just enoughh for the required functionality.
 
 * I am assuming that this would be a connector to another service, or database, so wrapped all calls in Futures to emulate this.
 * Another implementation can added and configured using the dependency injection in Module.scala
 
2) The routes have an IP filter configured, as I would assume this would point to an upstream load balancer or proxy and not be exposed directly
test