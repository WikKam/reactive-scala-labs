package EShop.lab4

import EShop.lab2.TypedCartActor.Event
import EShop.lab2.{Cart, TypedCheckout}
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable = context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      Empty,
      commandHandler(context),
      eventHandler(context)
    )
  }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty => command match {
        case AddItem(item) => Effect.persist(ItemAdded(item))
        case GetItems(sender) => Effect.reply(sender)(Cart.empty)
        case StartCheckout(_) => Effect.none
        case RemoveItem(_) => Effect.none
      }

      case NonEmpty(cart, _) => command match {
        case AddItem(item) => Effect.persist(ItemAdded(item))
        case GetItems(sender) => Effect.reply(sender)(cart)
        case ExpireCart => Effect.persist(CartExpired)
        case RemoveItem(item) => if (state.cart.contains(item)) {
          Effect.persist(if (state.cart.size == 1) CartEmptied else ItemRemoved(item))
        } else {
          Effect.none
        }
        case StartCheckout(orderManagerRef) => Effect.persist(CheckoutStarted(orderManagerRef)).thenRun(state => {
            val typedCheckout = context.spawn(new TypedCheckout(context.self).start, "TypedCheckout")
            typedCheckout ! TypedCheckout.StartCheckout
            orderManagerRef ! CheckoutStarted(typedCheckout)
          }
        )
      }

      case InCheckout(_) => command match {
        case ConfirmCheckoutClosed => Effect.persist(CheckoutClosed)
        case ConfirmCheckoutCancelled => Effect.persist(CheckoutCancelled)
        case AddItem(_) => Effect.none
      }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    event match {
      case CheckoutStarted(_)        =>
        state.timerOpt match {
          case Some(timer) => timer.cancel()
          case None =>
        }
        InCheckout(state.cart)
      case ItemAdded(item)           =>
        state.timerOpt match {
          case Some(timer) => timer.cancel()
          case None =>
        }
        NonEmpty(state.cart.addItem(item), scheduleTimer(context))
      case ItemRemoved(item)         =>
        state.timerOpt match {
          case Some(timer) => timer.cancel()
          case None =>
        }
        val cartWithItemRemoved = state.cart.removeItem(item)
        NonEmpty(cartWithItemRemoved, state.timerOpt.get)
      case CartEmptied | CartExpired =>
        state.timerOpt match {
          case Some(timer) => timer.cancel()
          case None =>
        }
        Empty
      case CheckoutClosed            => Empty
      case CheckoutCancelled         => NonEmpty(state.cart, scheduleTimer(context))
    }
  }

}
