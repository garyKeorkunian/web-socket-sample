package com.keorkunian.socketsample

import org.mashupbots.socko.webserver.{WebServer, WebServerConfig}
import akka.actor.{Props, ActorSystem}

object Main extends TimeServiceRoute {

  implicit val system = ActorSystem("web-socket-sample")
  val webServer: WebServer = new WebServer(WebServerConfig(), timeServiceRoutes, system)

  def main(args: Array[String]) {
    println("Starting Web Socket Sample")

    // Start the TimeService
    system.actorOf(Props[TimeServiceActor]) ! TimeServiceActor.Tick

    // Start the TimeServiceSocketSupervisor
    system.actorOf(Props[TimeServiceSocketSupervisor], TimeServiceSocketSupervisor.actorName)

    // Start the Web Service
    webServer.start()
  }
}

