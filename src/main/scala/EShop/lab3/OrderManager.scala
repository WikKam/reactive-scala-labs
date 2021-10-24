package EShop.lab3

import EShop.lab2.TypedCartActor.StartCheckout
import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  //case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  //case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  //case object ConfirmPaymentReceived                                                                  extends Command
  private final case class Response(message: Any) extends Command
  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager {

  import OrderManager._

  def start: Behavior[OrderManager.Command] = uninitialized

  def uninitialized: Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case AddItem(id, sender) =>
          val cart = context.spawn(new TypedCartActor().start, "TypedCart")
          cart ! TypedCartActor.AddItem(id)
          sender ! Done
          open(cart)
      }
  )

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, msg) =>
      msg match {
        case AddItem(id, sender) =>
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          open(cartActor)
        case RemoveItem(id, sender) =>
          cartActor ! TypedCartActor.RemoveItem(id)
          sender ! Done
          open(cartActor)
        case Buy(sender) =>
          cartActor ! TypedCartActor.StartCheckout(context.messageAdapter(message => Response(message)))
          inCheckout(cartActor, sender)
    }
  )
  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (_, msg) =>
      msg match {
        case wrapped: Response => wrapped.message match {
          case TypedCartActor.CheckoutStarted(checkoutActor) =>
            senderRef ! Done
            inCheckout(checkoutActor)
        }
      }
  )

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[OrderManager.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
        checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
        checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.messageAdapter(message => Response(message)))
        inPayment(sender)
    }
  )

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] = Behaviors.receive(
    (_, msg) =>
      msg match {
        case wrapped: Response => wrapped.message match {
          case TypedCheckout.PaymentStarted(payment) =>
            senderRef ! Done
            inPayment(payment, senderRef)
        }
      }
  )

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive((context, msg) =>
    msg match {
      case Pay(sender) =>
        paymentActorRef ! Payment.DoPayment
        inPayment(paymentActorRef, sender)
      case wrapped: Response =>
        wrapped.message match {
          case Payment.PaymentReceived =>
            senderRef ! Done
            finished
        }
    }
  )

  def finished: Behavior[OrderManager.Command] = Behaviors.stopped
}
