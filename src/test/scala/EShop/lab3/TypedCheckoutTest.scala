package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import TypedCheckout._

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val cartActorMock = createTestProbe[TypedCartActor.Command]()
    val orderManagerMock = createTestProbe[Any]()
    val checkoutTest = testKit.spawn(new TypedCheckout(cartActorMock.ref).start)
    checkoutTest ! StartCheckout
    checkoutTest ! SelectDeliveryMethod("mail")
    checkoutTest ! SelectPayment("paypal", orderManagerMock.ref)

    orderManagerMock.expectMessageType[PaymentStarted]

    checkoutTest ! ConfirmPaymentReceived

    cartActorMock.expectMessage(TypedCartActor.ConfirmCheckoutClosed)

  }

}
