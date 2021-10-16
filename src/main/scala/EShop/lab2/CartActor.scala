package EShop.lab2

import EShop.lab2.Checkout.CancelCheckout
import akka.actor.{Actor, ActorContext, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props(customer: Option[ActorRef] = None) = Props(new CartActor(customer))
}

class CartActor(customer: Option[ActorRef] = None) extends Actor {

  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5 seconds

  val checkout: ActorRef = context.actorOf(Checkout.props(Some(self), customer))

  private def scheduleTimer: Cancellable = context
    .system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  private def addItemAndChangeContext(item: Any, context: ActorContext): Unit = {
    val cartWithItem = Cart.empty.addItem(item)
    context become nonEmpty(cartWithItem, scheduleTimer)
  }

  private val onCartIsEmpty: (Cancellable, ActorContext) => Unit = (timer, context) => {
    timer.cancel()
    context become empty
  }

  private val onCartExpire = onCartIsEmpty

  def receive: Receive = LoggingReceive {
    case AddItem(item) => addItemAndChangeContext(item, context)
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item) => addItemAndChangeContext(item, context)
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) => context become nonEmpty(cart.addItem(item), timer)
    case RemoveItem(item) if cart.size > 0 && cart.contains(item) =>
      val cartWithItemRemoved = cart.removeItem(item)
      if(cartWithItemRemoved.size == 0){
        onCartIsEmpty(timer, context)
      }
      else context become nonEmpty(cartWithItemRemoved, timer)
    case ExpireCart => onCartExpire(timer, context)
    case StartCheckout =>
      timer.cancel()
      checkout ! StartCheckout
      context become inCheckout(cart)
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutClosed | Checkout.CheckOutClosed =>
      context become empty
    case ConfirmCheckoutCancelled =>
      checkout ! CancelCheckout
      context become nonEmpty(cart, scheduleTimer)
  }


}
