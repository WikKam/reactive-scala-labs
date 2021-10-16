package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorContext, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event
  def props(cart: Option[ActorRef] = None, customer: Option[ActorRef] = None) = Props(new Checkout(cart, customer))

}

class Checkout(cart: Option[ActorRef] = None, customer: Option[ActorRef] = None) extends Actor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration = 1 seconds

  private val onStop: (Option[ActorRef], ActorContext) => Unit = (customer, context) => {
    if (customer.isDefined) customer.get ! Uninitialized
    context stop self
  }

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      context become selectingDelivery(scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout =>
      timer.cancel()
      context become cancelled
    case ExpireCheckout => context become cancelled
    case SelectDeliveryMethod(_) =>
      if(customer.isDefined) customer.get ! SelectingDeliveryStarted(timer)
      context become selectingPaymentMethod(timer)

  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout =>
      timer.cancel()
      context become cancelled
    case ExpireCheckout => context become cancelled
    case SelectPayment(_) =>
      timer.cancel()
      context become processingPayment(scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment))
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout =>
      timer.cancel()
      context become cancelled
    case ExpirePayment => context become cancelled
    case ConfirmPaymentReceived =>
      timer.cancel()
      if(customer.isDefined) {
        customer.get ! ProcessingPaymentStarted(timer)
        customer.get ! CheckOutClosed
      }
      if(cart.isDefined) cart.get ! CheckOutClosed
      context become closed
  }

  def cancelled: Receive = LoggingReceive {
    case _ => onStop(customer, context)
  }

  def closed: Receive = LoggingReceive {
    case _ => onStop(customer, context)
  }

}
