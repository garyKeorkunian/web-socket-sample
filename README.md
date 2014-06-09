Web Socket Sample
=================

This application provides a very simple example for establishing a
web socket connection and pushing data to the client


Currently there is one demo, the Time Service

To run the application:

1)  Clone this repo (`git clone https://github.com/garyKeorkunian/web-socket-sample`)

2)  Run it (`sbt run`)

This will launch the web service and start the time service

3)  User your browser to open `src/main/web/timeservice.html`

This will load a page that starts some JS to open a web socket connection to the service.
On each `Tick` of the service, the time in millis will be pushed to the browser and an event handler
in the JS will update the output div.

The web service is based on the Akka and the Socko Web Server.

