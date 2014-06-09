package socktest

import org.mashupbots.socko.webserver.{WebServer, WebServerConfig}
import org.mashupbots.socko.routes.{PathSegments, Routes, WebSocketHandshake}
import akka.actor.{OneForOneStrategy, Props, Actor, ActorSystem}
import scala.concurrent.duration._
import akka.actor.SupervisorStrategy.Restart

object Main extends SocketRoute {

  implicit val system = ActorSystem("sockets")

  def main(args: Array[String]) {
    println("Starting SockTest")
    system.actorOf(Props[NewDataFactory]) ! NewDataFactory.Tick
    system.actorOf(Props[WebSocketSupervisor], "websocket")
    webServer.start()
  }
}

trait SocketRoute {

  def system: ActorSystem

  def onWebSocketHandshakeComplete(webSocketId: String) {
    System.out.println(s"Web Socket $webSocketId connected")
    system.actorSelection("user/websocket") ! WebSocketSupervisor.RegisterWebSocket(webSocketId)
  }

  def onWebSocketClose(webSocketId: String) {
    System.out.println(s"Web Socket $webSocketId closed")
    system.actorSelection(webSocketId) ! WebSocketActor.Stop
  }

  val webServer = new WebServer(WebServerConfig(), Routes {
    case WebSocketHandshake(wsHandshake) => wsHandshake match {
      case PathSegments("websocket" :: Nil) => {
        wsHandshake.authorize(
          onComplete = Some(onWebSocketHandshakeComplete),
          onClose = Some(onWebSocketClose))
      }
    }
  }, system)
}

object WebSocketSupervisor {
  case class RegisterWebSocket(webSocketId: String)
}
class WebSocketSupervisor extends Actor {
  import WebSocketSupervisor._

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 3) {
    case _: Exception                                 ⇒ Restart
  }

  def receive = {
    case RegisterWebSocket(wsId) =>
      println(s"RegisterWebSocket($wsId)")
      context.actorOf(Props(new WebSocketActor(wsId)), wsId)
  }
}

object WebSocketActor {
  case class Stop()
}
class WebSocketActor(webSocketId: String) extends Actor {
  import WebSocketActor._
  import NewDataFactory._

  override def preStart() {
    context.system.eventStream.subscribe(self, classOf[NewData])
  }

  override def postStop() {
    Main.webServer.webSocketConnections.close(webSocketId)
  }

  def receive = {
    case NewData(data) =>
      println(s"NewData($data)")
      Main.webServer.webSocketConnections.writeText(data, webSocketId)

    case Stop =>
      context.stop(self)
  }
}

object NewDataFactory {
  case class Tick()
  case class NewData(data: String)
}

class NewDataFactory extends Actor {
  import NewDataFactory._
  import context.dispatcher

  def receive = {
    case Tick =>
      val millis = System.currentTimeMillis().toString
      println(s"Tick => $millis")
      context.system.eventStream.publish(NewData(millis))
      context.system.scheduler.scheduleOnce(1000 milliseconds, self, Tick)
  }
}

