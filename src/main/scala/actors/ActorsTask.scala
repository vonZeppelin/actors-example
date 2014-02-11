package actors

import akka.actor._
import scala.util.Random


object ActorsTask {
  val total = 5
  trait Message

  case class SendToNode(target: Int, message: Int) extends Message
  case class ReceiveFromNode(message: Int) extends Message
}

/**
 * A Master actor that spawns Node actors.
 */
class ActorsTask extends Actor {
  import ActorsTask._

  for (i <- 1 to total) {
    context.actorOf(Props(classOf[Node], i, total), "Node-" + i)
  }

  def receive: Receive = Actor.emptyBehavior

}

/**
 * A Node actor, it knows its own id, total number of nodes and keeps a secret value. The Node actor with id = 1
 * becomes an "accumulator" that waits for (total - 1) messages from ordinary nodes, calculates the sum and sends the
 * result to others. An ordinary Node (id > 1) sends its secret to an accumulator Node on start and then just waits
 * for the result.
 */
class Node(n: Int, total: Int) extends Actor with ActorLogging {
  import ActorsTask._

  val secret = Random.nextInt(Integer.MAX_VALUE / total)
  val isAcc = n == 1

  // use EventStream instead of actor lookup by its name...
  context.system.eventStream.subscribe(self, classOf[Message])

  override def preStart: Unit = {
    if (isAcc) {
      // what if there is only accumulator?
      if (total == 1) {
        self ! SendToNode(1, 0)
      }
    } else {
      // ordinary node sends a secret to the accumulator
      context.system.eventStream.publish(SendToNode(1, secret))
    }
  }

  def ordinary: Receive = {
    case ReceiveFromNode(sum) => {
      log.info("My number is {} and I know the sum: {}", n, sum)
    }
  }

  def accumulator(sum: Int, toReceive: Int): Receive = {
    case SendToNode(target, message) if target == n => {
      val newSum = sum + message
      if (toReceive > 1) {
        context.become(accumulator(newSum, toReceive - 1))
      } else {
        log.info("My number is {} and I know the sum: {}", n, newSum)
        context.system.eventStream.publish(ReceiveFromNode(newSum))
        // stop the Master actor
        context.parent ! PoisonPill
      }
    }
  }

  def receive = if (isAcc) accumulator(secret, total - 1) else ordinary

}
