package swimr

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object DbActor {
	sealed trait Command
	case object Start extends Command
	final case class Msg(m:String) extends Command

	var count = 0

	def apply():Behavior[Command] = {
		println("[DbActor:apply]")

		Behaviors.receive{ (context, cmd:Command) => {
			cmd match {
				case Start => {
					println("[DbActor] starting")

				}
				case Msg(m) => {
					println(s"[DbActor] received: ${m}, context: ${context}")
					println("[DbActor] starting long db action " + count)
					Thread.sleep(5000)
					println("[DbActor] done with long db action " + count)
					count+=1
				}
			}
			Behaviors.same
		}}
	}
}
