package swimr

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object DbActor {
	sealed trait Command
	case object Start extends Command
	final case class Msg(m:String, sender:akka.actor.typed.ActorRef[String]) extends Command

	def apply():Behavior[Command] = {
		println("[DbActor:apply]")

		Behaviors.receive{ (context, message) => {
			message match {
				case Start => {
					println("[DbActor] starting")

				}
				case Msg(m, sender) => {
					println(s"[DbACtor] received ${m} from ${sender}")
				}
			}
			Behaviors.same
		}}
	}
}
