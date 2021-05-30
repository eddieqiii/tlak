package com.github.eddieqiii.tlak

import scala.io.StdIn.readLine

import akka.actor.typed.ActorSystem

object Driver extends App {
  val system = ActorSystem(TlakSystem(), "system")

  var exit = false
  while (!exit) {
    print("(r)egister, (d)isconnect, (m)essage, (e)xit: ")
    readLine().trim() match {
      case "r" => {
        print("name: ")
        system ! TlakSystem.Register(readLine().trim())
      }
      case "d" => {
        print("name: ")
        system ! TlakSystem.Disconnected(readLine().trim())
      }
      case "m" => {
        print("from user: ")
        val from = readLine().trim()
        print("  to user: ")
        val to = readLine().trim()
        print("  message: ")
        val body = readLine().trim()

        system ! TlakSystem.TellSend(from, to, body)
      }
      case "e" => exit = true
    }
  }
}
