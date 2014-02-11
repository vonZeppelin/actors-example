package actors

import scala.concurrent.duration._
import scala.util.Random

import org.scalatest._

import com.typesafe.config.ConfigFactory

import akka.actor._
import akka.testkit._


class ActorsTaskSuite(_sys: ActorSystem) extends TestKit(_sys)
    with FunSuite
    with BeforeAndAfterAll
    with ImplicitSender
    with OneInstancePerTest {

  import actors.ActorsTask._

  def this() = this(ActorSystem("ActorsTaskSpec", ConfigFactory.parseString("""
      akka.loggers = ["akka.testkit.TestEventListener"]
  """)))

  override def afterAll: Unit = system.shutdown()

  system.eventStream.subscribe(self, classOf[Message])

  test("an accumulator node when it's alone") {
    EventFilter.info(message = "My number is 1 and I know the sum: 100", occurrences = 1) intercept {
      val accNode = system.actorOf(Props(new Node(1, 1) {
        override val secret = 100
      }))

      expectMsg(ReceiveFromNode(100))
    }
  }

  test("an accumulator node when it's not alone") {
    val n = 5
    val accNode = system.actorOf(Props(new Node(1, n) {
      override val secret = 10
    }))

    expectNoMsg()

    EventFilter.info(message = "My number is 1 and I know the sum: 20", occurrences = 1) intercept {
      for (i <- 1 until n) {
        accNode ! SendToNode(1, i)
      }

      expectMsg(ReceiveFromNode(10 + (1 until n).sum))
    }
  }

  test("an ordinary node") {
    val node = system.actorOf(Props(new Node(5, 5) {
      override val secret = 50
    }))

    expectMsg(SendToNode(1, 50))

    EventFilter.info(message = "My number is 5 and I know the sum: 60", occurrences = 1) intercept {
      node ! ReceiveFromNode(50 + 10)
    }
  }

  test("all together now") {
    val n = 100
    val secrets = Vector.fill(n - 1)(Random.nextInt(Int.MaxValue / n))
    val accNode = system.actorOf(Props(new Node(1, n) {
      override val secret = 0
    }))
    val nodes = for (i <- 2 to n) yield {
      system.actorOf(Props(new Node(i, n) {
        override val secret = secrets(i - 2)
      }))
    }

    if (!receiveN(n - 1).forall {
      case SendToNode(1, x) => secrets contains x
      case _ => false
    }) fail

    expectMsg(15.seconds, ReceiveFromNode(secrets.sum))
  }

}
