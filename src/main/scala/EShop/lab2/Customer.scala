package EShop.lab2

import EShop.lab2.CartActor.{AddItem, CheckoutStarted, RemoveItem}
import EShop.lab2.Checkout.{CheckOutClosed, ConfirmPaymentReceived, PaymentStarted, ProcessingPaymentStarted, SelectDeliveryMethod, SelectPayment, SelectingDeliveryStarted, StartCheckout, Uninitialized}
import EShop.lab2.Customer.Initialize
import akka.actor.{Actor, ActorRef}
import akka.event.{Logging, LoggingReceive}

object Customer {
  case object Initialize
}

class Customer extends Actor {

  val cart: ActorRef = context.actorOf(CartActor.props(Some(self)))
  private val log    = Logging(context.system, this)

  def receive: Receive = LoggingReceive {

    case Initialize =>
      cart ! AddItem("item1")
      cart ! AddItem("item2")
      cart ! AddItem("item3")
      cart ! RemoveItem("item2")
      cart ! RemoveItem("item3")
      cart ! StartCheckout

    case CheckoutStarted(checkout: ActorRef) =>
      checkout ! SelectDeliveryMethod("deliveryMethod1")
      checkout ! SelectPayment("paymentMethod1")

    case CheckOutClosed =>
      log.debug("[Customer]: Checkout is closed")

    case PaymentStarted(payment: ActorRef) =>
      payment ! ConfirmPaymentReceived

    case ProcessingPaymentStarted(timer) =>
      log.debug("[Customer]: payment processing has started")

    case SelectingDeliveryStarted(timer) =>
      log.debug("[Customer]: selecting delivery has started")

    case Uninitialized =>
      log.debug("[Customer]: checkout got uninitialized")
  }
}
