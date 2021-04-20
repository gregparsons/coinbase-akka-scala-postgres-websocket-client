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
import upickle.default._

object PingActor{

//	var cx:Option[Cancellable] = None

	def apply(wsActor:ActorRef[WebsocketActor.Ping]):Behavior[String] = {

		Behaviors.receive( (context, message) => {

			message match {

				case "start" => {
					println("[PingActor] 'start' received")
//
//					// val wsActor:ActorRef[WebsocketActor.Ping] = context.spawn(WebsocketActor(), "pong-actor")
//					println("[PingActor] starting timed pings to WebsocketActor")
//
//					import scala.concurrent.ExecutionContext.Implicits.global
//					import scala.concurrent.duration.FiniteDuration
//					import scala.concurrent.duration.MILLISECONDS
//
//					cx = Some(context.system.scheduler.scheduleWithFixedDelay(
//						FiniteDuration(0,MILLISECONDS),
//						FiniteDuration(3000,MILLISECONDS))(
//							new Runnable(){ def run() = {
//								wsActor ! WebsocketActor.Ping(context.self)
//							}
//						})
//					)
				}

				case "stop" => {

					println("[PingActor] 'stop' received")

//					cx match {
//						case None => {
//							println("[PingActor] 'stop' received, but cx is None")
//						}
//						case Some(cancellable) => {
//							println("[PingActor] 'stop' received, canceling the cancellable")
//							cancellable.cancel();
//						}
//					}
				}

				case "confirmed" =>{
					println("[PingActor] 'confirmed' received")
				}
			}

			Behaviors.same
		})
	}
}


object WebsocketActor {

	sealed trait Command
	final case class Ping(sender:ActorRef[String]) extends Command
	case object Start extends Command
	case object Stop extends Command

	var db: ActorRef[DbActor.Command] =null
//	var ctx:ActorRef[WebsocketActor.Command] = null

	val ws = startSimpleWebsocket

	def apply(dbActor: ActorRef[DbActor.Command]):Behavior[WebsocketActor.Command] = {

		db = dbActor

		Behaviors.receive( (context, message) => {
			message match {
				case Ping(sender) => {
					// message.sender ! "confirmed"
					ws ! "[WebsocketActor.Ping] hello? i got a ping"
				}
				case Start => {
					???
				}
				case Stop => {
					???
				}
			}
			Behaviors.same
		})
	}

	def generateSubscribeJson: String = {

		//
		//		{
		//			"type": "subscribe",
		//			"product_ids": [
		//			"ETH-USD",
		//			"ETH-EUR"
		//			],
		//			"channels": [
		//			"level2",
		//			"heartbeat",
		//			{
		//				"name": "ticker",
		//				"product_ids": [
		//				"ETH-BTC",
		//				"ETH-USD"
		//				]
		//			}
		//			]
		//		}
		val subscribeJson = ujson.Obj(
			"type"->ujson.Str("subscribe"),
			"product_ids"-> ujson.Arr("BTC-USD"),
			"channels"-> ujson.Arr(/*"level2","heartbeat", */"ticker")

		)
		println("[generateJson] subscribe: " + ujson.write(subscribeJson))
		ujson.write(subscribeJson)

	}

	def startSimpleWebsocket: Websocket = {


		val jsonSubscribe = generateSubscribeJson
		println(s"[startSimpleWebsocket] json: ${jsonSubscribe}")


		// wss://ws-feed-public.sandbox.pro.coinbase.com


//		val cli:WebsocketClient[String] = WebsocketClient[String]("ws://echo.websocket.org") { wsMessage=>{
		val cli:WebsocketClient[String] = WebsocketClient[String]("wss://ws-feed-public.sandbox.pro.coinbase.com") { wsMessage=>{

			// println(s"[WebsocketClient] websocket text: ${wsMessage}")

			// persist the message to the database
			// println(s"[WebsocketClient] sending message to db")
			db ! DbActor.Msg(wsMessage)
			// println(s"[WebsocketClient] sent message to db")
		}}
		val ws = cli.open()


		// send coinbase's subscribe message per:
		// https://docs.pro.coinbase.com/#protocol-overview
		ws ! jsonSubscribe

		ws
	}
}

object MainActor {

	trait Command
	object Start extends Command
	object ShutItAllDown extends Command

	// the type of message to be handled is declared to be of type SayHello
	// which means the message argument has the same type
	def apply(): Behavior[Command] = {
		println("[MainActor::apply] MainActor alive")

		// bootstrap the actor system
		Behaviors.setup({ context =>
			println("[MainActor.apply]")

			val currentPriceActor = context.spawn(CurrentPriceActor(), "currentPriceActor")
			val dbActor = context.spawn(swimr.DbActor(currentPriceActor), "dbActor")
			val wsActor = context.spawn(WebsocketActor(dbActor), "wsActor")
			val pingActor = context.spawn(PingActor(wsActor), "pinger")

			// pattern match not needed here because message will (even) implicitly
			// get the SayHello type ([T])
			Behaviors.receiveMessage(message => {

				message match {
					case Start =>
						println("[MainActor] sending 'start' to PingActor")
						// "the next behavior is the same as the current one"
						currentPriceActor ! CurrentPriceActor.Start
//						pingActor ! "start"

					case ShutItAllDown => {
//						pingActor ! "stop" // already
						currentPriceActor ! CurrentPriceActor.Stop
					}
				}

				Behaviors.same
			})
		})
	}
}

object Main extends App {

	val myActorMain:ActorSystem[MainActor.Command] = ActorSystem(MainActor(),"MainActor")

	myActorMain ! MainActor.Start
	Thread.sleep(300000)
	myActorMain ! MainActor.ShutItAllDown


}
