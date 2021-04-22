package swimr
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, SupervisorStrategy}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.github.andyglow.websocket._
import swimr.DbActor.YouThere
import upickle.default._



object MainActor {

	trait Command
	object Start extends Command
	object ShutItAllDown extends Command
	object DbStarted extends Command
	object WsStarted extends Command
	object PriceStarted extends Command

	var ctx:ActorContext[MainActor.Command] = null

	var dbStarted = false
	var wsStarted = false
	var priceStarted = false

	// var currentPriceActor:ActorRef[CurrentPriceActor.Command] = null
	// var dbActor:ActorRef[DbActor.Command] = null
	// var wsActor:ActorRef[WsActor.Command] = null

	def apply(): Behavior[Command] = {
		println("[MainActor::apply] MainActor alive")
		setupBehavior
	}

	def setupBehavior:Behavior[Command] = {

		Behaviors.supervise[MainActor.Command] {

			Behaviors.setup({ context: ActorContext[MainActor.Command] =>

				ctx = context

				val currentPriceActor = ctx.spawn(CurrentPriceActor(), "currentPriceActor")
				val dbActor = ctx.spawn(swimr.DbActor(currentPriceActor), "dbActor")
				val wsActor = ctx.spawn(WsActor(dbActor), "wsActor")

				Behaviors.receiveMessage[Command] {

					case Start =>

						println("[MainActor] Start command received.")
						currentPriceActor ! CurrentPriceActor.Start

						dbActor ! DbActor.YouThere(context.self)
						dbActor ! DbActor.ConnectToPostgres

						wsActor ! WsActor.YouThere(context.self)
						Behaviors.same

					case DbStarted =>
						println("[MainActor] rcvd: DbStarted")
						dbStarted = true
						isEveryoneReady
						Behaviors.same

					case WsStarted =>
						println("[MainActor] WsStarted")
						wsStarted = true
						isEveryoneReady
						wsActor ! WsActor.Start
						Behaviors.same

					case PriceStarted =>
						priceStarted = true
						Behaviors.same

					case ShutItAllDown =>
						currentPriceActor ! CurrentPriceActor.Stop
						Behaviors.stopped

				}.receiveSignal {
					case (context, PostStop) => {
						println("[MainActor] actor system stopped")
						Behaviors.same
					}
				}
			})
		}.onFailure(SupervisorStrategy.restart)




	}

	def isEveryoneReady(): Unit ={

		if (dbStarted && wsStarted) {
			println("[isEveryoneStarted] db and ws ready")
		}

	}

}

object Main extends App {


	val system:ActorSystem[MainActor.Command] = ActorSystem(MainActor(),"MainActor")

	system ! MainActor.Start
//	Thread.sleep(80000)
//	system ! MainActor.ShutItAllDown

}
