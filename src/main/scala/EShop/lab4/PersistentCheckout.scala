package EShop.lab4

import EShop.lab2.TypedCartActor.ConfirmCheckoutClosed
import EShop.lab2.TypedCheckout.CancelCheckout
import EShop.lab2.{TypedCartActor, TypedCheckout}
import EShop.lab3.{OrderManager, Payment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  def schedule(context: ActorContext[Command]): Cancellable = context.scheduleOnce(timerDuration, context.self, ExpireCheckout)

  def apply(cartActor: ActorRef[TypedCartActor.Command], persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context, cartActor),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCartActor.Command]
  ): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case WaitingForStart => command match {
        case StartCheckout => Effect.persist(CheckoutStarted)
      }
      case SelectingDelivery(_) => command match {
        case SelectDeliveryMethod(method) => Effect.persist(DeliveryMethodSelected(method))
        case CancelCheckout | ExpireCheckout => Effect.persist(CheckoutCancelled)
      }

      case SelectingPaymentMethod(_) => command match {
        case SelectPayment(payment, orderManagerRef) => Effect.persist(PaymentStarted(orderManagerRef))
          .thenRun(_ => {
            val paymentRef = context.spawn(new Payment(payment, orderManagerRef, context.self).start, "Payment")
            orderManagerRef ! PaymentStarted(paymentRef)
          })
        case CancelCheckout | ExpireCheckout => Effect.persist(CheckoutCancelled)
      }

      case ProcessingPayment(_) => command match {
        case CancelCheckout | ExpireCheckout => Effect.persist(CheckoutCancelled)
        case ConfirmPaymentReceived => Effect.persist(CheckOutClosed)
          .thenRun(_ => cartActor ! ConfirmCheckoutClosed)
      }

      case Cancelled => command match {
        case _ => Effect.none
      }

      case Closed => command match {
        case _ => Effect.none
      }

    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    event match {
      case CheckoutStarted           =>
        SelectingDelivery(schedule(context))
      case DeliveryMethodSelected(_) =>
        state.timerOpt match {
          case Some(timer) => timer.cancel()
          case None =>
        }
        SelectingPaymentMethod(schedule(context))
      case PaymentStarted(_)         =>
        state.timerOpt match {
          case Some(timer) => timer.cancel()
          case None =>
        }
        ProcessingPayment(schedule(context))
      case CheckOutClosed            =>
        state.timerOpt match {
          case Some(timer) => timer.cancel()
          case None =>
        }
        Closed
      case CheckoutCancelled         =>
        state.timerOpt match {
          case Some(timer) => timer.cancel()
          case None =>
        }
        Cancelled
    }
  }
}
