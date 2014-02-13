package actors

import akka.actor._
import scala.util.Random


object ActorsTask {
  val total = 10

  sealed trait Message
  case class SendToNode(target: Int, message: Int) extends Message
  case class ReceiveFromNode(message: Int) extends Message
  object Done extends Message

  // should return tuple, but returns Seq for convenience
  def myChildren(n: Int, total: Int): Seq[Option[Int]] = {
    val left = 2 * n
    val right = left + 1
    List(if (left <= total) Some(left) else None, if (right <= total) Some(right) else None)
  }
}

/**
 * A Master actor that spawns Node actors.
 */
class ActorsTask extends Actor {
  import ActorsTask._

  for (i <- 1 to total) {
    context.actorOf(Props(classOf[Node], i, total), "Node-" + i)
  }

  def waitForCompletion(n: Int): Receive = {
    case Done => if (n > 1) context.become(waitForCompletion(n - 1)) else context.stop(self)
  }

  def receive: Receive = waitForCompletion(total)
}

/**
 * A Node actor, it knows its own id, total number of nodes and keeps a secret value. Nodes are organized into a
 * "logical" binary tree: the node with id = 1 is a root with others being ordinary nodes and leaves, a node with
 * id = x has children with ids = (2 * x, 2 * x + 1) and a parent with id = floor(x / 2). The idea is to aggregate
 * the sum starting from leaves moving towards the root and then propagate the result back from the root to the leaves.
 */
class Node(n: Int, total: Int) extends Actor with ActorLogging {
  import ActorsTask._

  val secret = Random.nextInt(Integer.MAX_VALUE / total)
  val children = myChildren(n, total)

  override def preStart: Unit = self ! SendToNode(n, secret)

  def sendToNode(node: Int, msg: Message): Unit = context.actorSelection("../Node-" + node) ! msg

  def printAndPropagate(sum: Int) {
    log.info("My number is {} and I know the sum: {}", n, sum)
    // propagate sacred knowledge to my children
    children foreach {
      case Some(node) => sendToNode(node, ReceiveFromNode(sum))
      case _ =>
    }
    context.parent ! Done
  }

  def waitForChildren(subtreeSum: Int, remaining: Int): Receive = {
    case SendToNode(target, message) if target == n => {
      val newSum = subtreeSum + message
      if (remaining > 1) {
        context.become(waitForChildren(newSum, remaining - 1))
      } else {
        if (n == 1) { // I'm the root and aggregation is complete
          printAndPropagate(newSum)
        } else { // send the sum of the subtree to a parent node
          sendToNode(n / 2, SendToNode(n / 2, newSum))
          context.become(waitForParent)
        }
      }
    }
  }

  def waitForParent: Receive = {
    case ReceiveFromNode(sum) => printAndPropagate(sum)
  }

  def receive = waitForChildren(0, (children count (_.isDefined)) + 1)
}
