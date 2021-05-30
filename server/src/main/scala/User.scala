package com.github.eddieqiii.tlak

import scala.collection.immutable.{HashMap, Queue}

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.{Behaviors}

// have each user handle the websocket connection ????
object User {
  type UserActor = ActorRef[User.Command]
  type ParentActor = ActorRef[TlakSystem.Command]

  sealed trait Command
  case class Message(from: String, body: String) extends Command
  case class Send(to: String, body: String) extends Command // TODO replace w/ ws
  case class ResolvedRef(name: String, ref: UserActor) extends Command
  case class UnresolvedRef(name: String) extends Command
  case object Disconnect extends Command

  def apply(name: String, parent: ParentActor): Behavior[Command] = 
    user(UserInfo(name, parent, HashMap.empty, Queue.empty))

  // could this be better implemented as a class? yes.
  private case class UserInfo(
    name:   String,
    parent: ParentActor,
    known:  HashMap[String, UserActor], // TODO use something like lru?
    queue:  Queue[Send] // TODO create timeout to keep annoying for queue
  )

  private def user(inf: UserInfo): Behavior[Command] =
    Behaviors.receive { (ctx, msg) => msg match {
      case Message(from, body) =>
        ctx.log.debug("[{}] Message received from '{}'", inf.name, from)
        Behaviors.same
      case toSend @ Send(to, body) =>
        ctx.log.debug("[{}] Sending message to '{}'", inf.name, to)
        inf.known get to match {
          case Some(ref) =>
            ref ! Message(inf.name, body)
            Behaviors.same
          case None =>
            ctx.log.debug("[{}] Asking to resolve name '{}'", inf.name, to)
            inf.parent ! TlakSystem.Resolve(to, ctx.self)
            user(inf.copy(queue = inf.queue enqueue toSend))
        }
      case ResolvedRef(name, ref) =>
        ctx.log.debug("[{}] Resolved name '{}'", inf.name, name)
        user(inf.copy(
          // TODO this is probably the biggest side effect in this
          // half-baked functional-style mess
          queue = inf.queue filter (_ match {
            case Send(to, body) if to == name =>
              ref ! Message(inf.name, body)
              false
            case _ => true
          }),
          known = inf.known + ((name, ref))
        ))
      case UnresolvedRef(name) =>
        ctx.log.debug("[{}] Could not resolve name '{}'", inf.name, name)
        user(inf.copy(queue = inf.queue filter (_.to != name))) // TODO tell someone
      case Disconnect =>
        ctx.log.debug("[{}] User disconnected, actor stopping", inf.name)
        Behaviors.stopped
    }}
}
