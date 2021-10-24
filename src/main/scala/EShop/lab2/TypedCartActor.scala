package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                             extends Command
  case class RemoveItem(item: Any)                                          extends Command
  case object ExpireCart                                                    extends Command
  case class StartCheckout(orderManagerRef: ActorRef[Any]) extends Command
  case object ConfirmCheckoutCancelled                                      extends Command
  case object ConfirmCheckoutClosed                                         extends Command
  case class ShowItems(sender: ActorRef[Cart])                              extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable = context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)


  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) => msg match {
      case AddItem(item) => nonEmpty(Cart.empty.addItem(item), scheduleTimer(context))
      case ShowItems(sender) =>
        sender ! Cart.empty
        Behaviors.same
    }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) => msg match {
      case  ShowItems(sender) =>
        sender ! cart
        Behaviors.same
      case AddItem(item) =>
        timer.cancel()
        nonEmpty(cart.addItem(item), timer)
      case RemoveItem(item) if cart.size > 0 && cart.contains(item) =>
        val cartWithItemRemoved = cart.removeItem(item)
        if(cartWithItemRemoved.size == 0) {
          timer.cancel()
          empty
        } else nonEmpty(cartWithItemRemoved, timer)
      case ExpireCart =>
        timer.cancel()
        empty
      case StartCheckout(orderManager) =>
        val typedCheckout = context.spawn(new TypedCheckout(context.self).start, "TypedCheckout")
        typedCheckout ! TypedCheckout.StartCheckout
        orderManager ! CheckoutStarted(typedCheckout)
        timer.cancel()
        inCheckout(cart)
    }
  )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case ConfirmCheckoutClosed =>
          empty
        case ConfirmCheckoutCancelled =>
          nonEmpty(cart, scheduleTimer(context))
      }
  )

}
