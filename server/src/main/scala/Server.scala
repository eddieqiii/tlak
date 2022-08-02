package com.github.eddieqiii.tlak

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import scala.io.StdIn
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl.{Flow, Source, Sink}

object Server {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(TlakSystem(), "tlak")
    implicit val executionContext = system.executionContext

    var counter = 0;

    val route = path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "akka-http"))
      }
    }

    val userRoute = path("user") {
      get {
        val name = "u" + counter.toString()
        system ! TlakSystem.Register(name)
        handleWebSocketMessages(userDriver(name, system))
      }
    }

    val bindingFuture = Http().newServerAt("0.0.0.0", 8080).bind(userRoute)

    StdIn.readLine()
    bindingFuture.flatMap(_.unbind()) // unbind from port
           .onComplete(_ => system.terminate()) // kill the actor system
  }

  def userDriver(name: String, system: ActorSystem[TlakSystem.Command]): Flow[Message, Message, Any] =
    Flow[Message].mapConcat {
      case tm: TextMessage =>
        tm.toString().split(' ').toList match {
          case "send" :: user :: msg :: Nil =>
            system ! TlakSystem.TellSend(name, user, msg)
            TextMessage(Source.single("sent!")) :: Nil
          case "disconnect" :: Nil =>
            system ! TlakSystem.Disconnected(name)
            Nil
          case _ => Nil
        }
        TextMessage(Source.single("hello!")) :: Nil
      case bm: BinaryMessage =>
        Nil
    }

}
