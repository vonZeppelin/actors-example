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

  object ForwardingActor extends Actor {
    def receive = {
      case x => testActor forward x
    }
  }

  import actors.ActorsTask._

  def this() = this(ActorSystem("ActorsTaskSpec", ConfigFactory.parseString("""
      akka.loggers = ["akka.testkit.TestEventListener"]
  """)))

  override def afterAll: Unit = system.shutdown()

  test("a root node when it's alone") {
    EventFilter.info(message = "My number is 1 and I know the sum: 100", occurrences = 1) intercept {
      val root = system.actorOf(Props(new Node(1, 1) {
        override val secret = 100
      }))
    }
  }

  test("a node with 1 child") {
    val n = 8
    val parent = system.actorOf(Props(ForwardingActor), "Node-2")
    val node = system.actorOf(Props(new Node(4, n) {
      override val secret = 10
    }))

    expectNoMsg()
    node ! SendToNode(4, 15)
    expectMsg(SendToNode(2, 25))

    EventFilter.info(message = "My number is 4 and I know the sum: 30", occurrences = 1) intercept {
      node ! ReceiveFromNode(30)
    }
  }

  test("a node with 2 children") {
    val n = 6
    val parent = system.actorOf(Props(ForwardingActor), "Node-1")
    val node = system.actorOf(Props(new Node(2, n) {
      override val secret = 50
    }))

    expectNoMsg()
    node ! SendToNode(2, 100)
    node ! SendToNode(2, 200)
    expectMsg(SendToNode(1, 350))

    EventFilter.info(message = "My number is 2 and I know the sum: 355", occurrences = 1) intercept {
      node ! ReceiveFromNode(355)
    }
  }

  test("a leaf") {
    val n = 6
    val parent = system.actorOf(Props(ForwardingActor), "Node-2")
    val node = system.actorOf(Props(new Node(5, n) {
      override val secret = 25
    }))

    expectMsg(SendToNode(2, 25))

    EventFilter.info(message = "My number is 5 and I know the sum: 5", occurrences = 1) intercept {
      node ! ReceiveFromNode(5)
    }
  }

  test("all together now") {
    val n = 100
    val secrets = Vector.fill(n - 1)(Random.nextInt(Int.MaxValue / n))
    val root = system.actorOf(Props(ForwardingActor), "Node-1")
    val nodes = for (i <- 2 to n) yield {
      system.actorOf(Props(new Node(i, n) {
        override val secret = secrets(i - 2)
      }), "Node-" + i)
    }

    val result = receiveN(2).foldLeft(0) {
      case (acc, SendToNode(1, sum)) => acc + sum
    }
    if (result != secrets.sum) fail
  }
}
