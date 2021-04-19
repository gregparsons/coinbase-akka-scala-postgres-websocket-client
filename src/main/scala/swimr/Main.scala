package swimr
import akka.actor.Cancellable
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.{Done, NotUsed}
//import akka.http.scaladsl.Http
//import akka.stream.scaladsl._
//import akka.http.scaladsl.model._
//import akka.http.scaladsl.model.ws._
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.Future
import com.github.andyglow.websocket._

object PingActor{

	var cx:Option[Cancellable] = None

	def apply():Behavior[String] = {

		Behaviors.receive( (context, message) => {

			message match {

				case "start" => {
					println("[PingActor] 'start' received")

					val wsActor:ActorRef[WebsocketActor.Ping] = context.spawn(WebsocketActor(), "pong-actor")
					println("[PingActor] sending 'ping' to WebsocketActor")

					import scala.concurrent.ExecutionContext.Implicits.global
					import scala.concurrent.duration.FiniteDuration
					import scala.concurrent.duration.MILLISECONDS

					cx = Some(context.system.scheduler.scheduleWithFixedDelay(
						FiniteDuration(0,MILLISECONDS),
						FiniteDuration(3000,MILLISECONDS))(
							new Runnable(){ def run() = {
								wsActor ! WebsocketActor.Ping(context.self)
							}
						})
					)
				}

				case "stop" => {

					println("[PingActor] 'stop' received")

					cx match {
						case None => {
							println("[PingActor] 'stop' received, but cx is None")
						}
						case Some(cancellable) => {
							println("[PingActor] 'stop' received, canceling the cancellable")
							cancellable.cancel();
						}
					}
				}

				case "confirmed" =>{
					println("[PingActor] 'confirmed' received")
				}
			}

			Behaviors.same
		})

	}
}


object WebsocketActor{
	final case class Ping(sender:ActorRef[String])

	val ws = startSimpleWebsocket

	def apply():Behavior[WebsocketActor.Ping] ={
		// Behaviors.setup(context => new WebsocketActor(context))

		Behaviors.receive( (context, message) => {
			println("[WebsocketActor] got a ping, sending 'confirmed'")
			ws ! "[WebsocketActor.onMessage] " + message
			message.sender ! "confirmed"
			Behaviors.same
		})
	}

	def startSimpleWebsocket: Websocket = {

		// 1. prepare ws-client
		// 2. define message handler
		val cli = WebsocketClient[String]("ws://echo.websocket.org") {
			case str =>
				//logger.info(s"<<| $str")
				println(s"<< echo | $str")
		}

		// 4. open websocket
		val ws = cli.open()

		// 5. send messages
		ws ! "hello"
		ws ! "world"

		ws

	}

}

object MainActor {

	trait Command
	final case class SayHello(message_text:String) extends Command
	final case class ShutItAllDown() extends Command

	// the type of message to be handled is declared to be of type SayHello
	// which means the message argument has the same type
	def apply(): Behavior[Command] = {
		println("[MyActorMain::apply] MyActorMain alive")

		// bootstrap the actor system
		Behaviors.setup({ context =>
			println("[MyActorMain.apply]")
			val pingActor = context.spawn(PingActor(), "pinger")
			// pattern match not needed here because message will (even) implicitly
			// get the SayHello type ([T])
			Behaviors.receiveMessage(message => {

				message match {
					case m:SayHello =>
						println("[GuardianActor] sending 'start' to PingActor")
						// "the next behavior is the same as the current one"
						pingActor ! "start"

					case m:ShutItAllDown => {
						pingActor ! "stop" // already
					}
				}


				Behaviors.same
			})
		})
	}
}

object Main extends App {

	val myActorMain:ActorSystem[MainActor.Command] = ActorSystem(MainActor(),"MyActorTest")

	myActorMain ! MainActor.SayHello("[Main] this is a message from Main to MyActorMain")

	 Thread.sleep(10000)
	 myActorMain ! MainActor.ShutItAllDown()


}
