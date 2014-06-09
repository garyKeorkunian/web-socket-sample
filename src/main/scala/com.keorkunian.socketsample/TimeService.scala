package com.keorkunian.socketsample

import akka.actor.{Props, OneForOneStrategy, Actor, ActorSystem}
import org.mashupbots.socko.routes.{PathSegments, WebSocketHandshake, Routes}
import akka.actor.SupervisorStrategy.Restart
import scala.concurrent.duration._

/**
 * This sample code demonstrates how to monitor and establish web socket requests
 * for clients that wish to subscribe to a server-side event stream.
 *
 * The TimeServiceRoute accepts incoming socket connection requests and notifies the
 * TimeServiceSocketSupervisor of all new registrations (connections).
 *
 * When client's close socket connections, the corresponding actor is sent a Stop message.
 */
trait TimeServiceRoute {

  def system: ActorSystem

  /**
   * Used to notify the supervisor of a new web socket registration (connection)
   * @param webSocketId String
   */
  def onWebSocketHandshakeComplete(webSocketId: String) {
    System.out.println(s"Web Socket $webSocketId connected")
    system.actorSelection(s"user/${TimeServiceSocketSupervisor.actorName}") ! TimeServiceSocketSupervisor.RegisterWebSocket(webSocketId)
  }

  /**
   * Used to stop the socket actor in response to the socket being closed
   * @param webSocketId String
   */
  def onWebSocketClose(webSocketId: String) {
    System.out.println(s"Web Socket $webSocketId closed")
    system.actorSelection(s"user/${TimeServiceSocketSupervisor.actorName}/$webSocketId") ! TimeServiceSocketActor.Stop
  }

  /**
   * Define a Socko Route that accepts incoming WebSocket requests.
   */
  val timeServiceRoutes = Routes {
    case WebSocketHandshake(wsHandshake) => wsHandshake match {
      case PathSegments("web-socket-sample" :: "time-service" :: Nil) => {
        wsHandshake.authorize(
          onComplete = Some(onWebSocketHandshakeComplete),
          onClose = Some(onWebSocketClose))
      }
    }
  }
}

/**
 * The TimeServiceSocketSupervisor is responsible for creating TimeServiceSocketActor for
 * each Registered Web Socket.
 */
object TimeServiceSocketSupervisor {
  val actorName = "time-service-socket-supervisor"
  case class RegisterWebSocket(webSocketId: String)
}
class TimeServiceSocketSupervisor extends Actor {
  import TimeServiceSocketSupervisor._

  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 3) {
    case _: Exception                                 ⇒ Restart
  }

  def receive = {
    case RegisterWebSocket(wsId) =>
      // Create a actor for the new web socket registration
      context.actorOf(Props(new TimeServiceSocketActor(wsId)), wsId)
  }
}

/**
 * A TimeServiceSocketActor is created for each client connection.
 * It subscribes to TimeData events and writes the time out to the
 * socket each time it receives that event.
 */
object TimeServiceSocketActor {
  case class Stop()
}
class TimeServiceSocketActor(webSocketId: String) extends Actor {
  import TimeServiceSocketActor._
  import TimeServiceActor._

  override def preStart() {
    // Subscribe to relevant events
    context.system.eventStream.subscribe(self, classOf[TimeData])
  }

  override def postStop() {
    // If this actor stops, close the corresponding socket
    Main.webServer.webSocketConnections.close(webSocketId)
  }

  def receive = {
    case TimeData(data) =>
      // Write the updated time to the socket
      Main.webServer.webSocketConnections.writeText(data.toString, webSocketId)

    case Stop =>
      // Shutdown this actor
      context.stop(self)
  }
}

/**
 * The TimeServiceActor is a simple service that publishes TimeData ~once per second to the eventStream.
 */
object TimeServiceActor {
  case class Tick()
  case class TimeData(timeInMillis: Long)
}

class TimeServiceActor extends Actor {
  import TimeServiceActor._
  import context.dispatcher

  def receive = {
    case Tick =>
      // Publish the millis and schedule the next Tick
      val millis = System.currentTimeMillis()
      context.system.eventStream.publish(TimeData(millis))
      context.system.scheduler.scheduleOnce(1 second, self, Tick)
  }
}
