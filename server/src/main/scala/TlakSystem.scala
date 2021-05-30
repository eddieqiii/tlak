package com.github.eddieqiii.tlak

import scala.collection.immutable.HashMap

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.{Behaviors}

object TlakSystem {
  type UserActor = ActorRef[User.Command]

  sealed trait Command
  case class TellSend(from: String, to: String, body: String) extends Command // TODO this is for debugging
  case class Resolve(name: String, replyTo: UserActor) extends Command
  case class Register(name: String) extends Command // TODO add ws info here
  case class Disconnected(name: String) extends Command // TODO watch child actors instead?

  def apply(): Behavior[Command] = system(SystemState(HashMap.empty))

  private case class SystemState(users: HashMap[String, UserActor])

  private def system(state: SystemState): Behavior[Command] =
    Behaviors.receive { (ctx, msg) => msg match {
      case TellSend(from, to, body) =>
        state.users get from match {
          case Some(ref) => ref ! User.Send(to, body)
          case None      => ctx.log.error("[system] Could not find user '{}'", from)
        }
        Behaviors.same
      case Resolve(name, ref) =>
        state.users get name match {
          case Some(resolved) => ref ! User.ResolvedRef(name, resolved)
          case None           => ref ! User.UnresolvedRef(name)
        }
        Behaviors.same
      case Register(name) =>
        ctx.log.debug("[system] User registered: '{}'", name)
        val newUser = ctx.spawn(User(name, ctx.self), "user:" + name)
        system(state.copy(
          users = state.users + ((name, newUser))
        ))
      case Disconnected(name) =>
        ctx.log.debug("[system] User disconnected: '{}'", name)
        system(state.copy(users = state.users - name))
    }}
}
