package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  it should "add item properly" in {
    val replyProbe = createTestProbe[Cart]()
    val cart = testKit.spawn(new TypedCartActor().start)
    val inbox = TestInbox[Cart]()
    cart ! AddItem("testitem")
    cart ! GetItems(replyProbe.ref)
    replyProbe.expectMessage(new Cart(List("testitem")))
  }

  it should "be empty after adding and removing the same item" in {
    val behaviorTestKit = BehaviorTestKit(new TypedCartActor().start)
    val inbox = TestInbox[Cart]()
    behaviorTestKit.run(AddItem("testitem"))
    behaviorTestKit.run(RemoveItem("testitem"))
    behaviorTestKit.run(GetItems(inbox.ref))
    inbox.expectMessage(Cart.empty)
  }

  it should "start checkout" in {
    val replyProbe = createTestProbe[Any]()
    val cart = testKit.spawn(new TypedCartActor().start)
    cart ! AddItem("testitem")
    cart ! StartCheckout(replyProbe.ref)
    replyProbe.expectMessageType[CheckoutStarted]
  }
}
