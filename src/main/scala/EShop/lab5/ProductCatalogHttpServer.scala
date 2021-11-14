package EShop.lab5

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}

object ProductCatalogHttpServer {
  case class SearchQuery(brand: String, keywords: List[String])

  case class SearchResult(results: List[ProductCatalog.Item])

}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val searchQueryFormat: RootJsonFormat[ProductCatalogHttpServer.SearchQuery] = jsonFormat2(ProductCatalogHttpServer.SearchQuery)
  implicit val uriFormat = new JsonFormat[URI] {
    override def write(obj: URI): spray.json.JsValue = JsString(obj.toString)

    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _ => throw new RuntimeException("Parsing exception")
      }
  }
  implicit val itemFormat: RootJsonFormat[ProductCatalog.Item] = jsonFormat5(ProductCatalog.Item)
  implicit val searchResultFormat: RootJsonFormat[ProductCatalogHttpServer.SearchResult] = jsonFormat1(ProductCatalogHttpServer.SearchResult)
}

object ProductCatalogHttpServerApp extends App {
  new ProductCatalogHttpServer().start(9000)
}


class ProductCatalogHttpServer extends JsonSupport {
  implicit val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "ProductCatalog")

  import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
  import akka.actor.typed.scaladsl.AskPattern.Askable

  implicit val timeout: Timeout = 3.seconds

  def routes(productCatalog: ActorRef[ProductCatalog.Query]): Route = {
    path("search") {
      get {
        entity(as[ProductCatalogHttpServer.SearchQuery]) { searchQuery =>
          onSuccess(productCatalog.ask(ProductCatalog.GetItems(searchQuery.brand, searchQuery.keywords, _))) {
            case ProductCatalog.Items(items) => complete(items)
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
      }
    }
  }

  def start(port: Int): Future[Done] = {
    val rout = onSuccess(system.receptionist.ask(Receptionist.Find(ProductCatalog.ProductCatalogServiceKey))){
      case ProductCatalog.ProductCatalogServiceKey.Listing(listings) =>
        listings
          .find(_ => true)
          .map(routes)
          .getOrElse(throw new IllegalStateException("ProductCatalog not found"))
    }
    val bindingFuture = Http().newServerAt("localhost", port).bind(rout)
    Await.ready(system.whenTerminated, Duration.Inf)
  }

}
