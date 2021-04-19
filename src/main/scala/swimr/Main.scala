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

	var cx:Option[Cancellable] = None

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


object WebsocketActor {

	sealed trait Command
	final case class Ping(sender:ActorRef[String]) extends Command
	final case class WsMessage(wsMsg:String) extends Command

	var db: ActorRef[DbActor.Command] =null
//	var ctx:ActorRef[WebsocketActor.Command] = null

	val ws = startSimpleWebsocket

	def apply(dbActor: ActorRef[DbActor.Command]):Behavior[WebsocketActor.Command] = {

		db = dbActor

		Behaviors.receive( (context, message) => {

//			ctx = context.self

			message match {
				case Ping(sender) => {
					// message.sender ! "confirmed"
					ws ! "[WebsocketActor.Ping] hello? i got a ping"

				}

				case WsMessage(wsMsg) =>{
					// tell the database what we got off the websocket
					???

				}
			}

			Behaviors.same
		})
	}

	def generateSubscribeJson: String = {

		// Request
		// Subscribe to ETH-USD and ETH-EUR with the level2, heartbeat and ticker channels,
		// plus receive the ticker entries for ETH-BTC and ETH-USD
		/*
			{
				"type": "subscribe",
				"product_ids": [
				"ETH-USD",
				"ETH-EUR"
				],
				"channels": [
				"level2",
				"heartbeat",
				{
					"name": "ticker",
					"product_ids": [
					"ETH-BTC",
					"ETH-USD"
					]
				}
				]
			}

		*/



		// [{"hello":"world","answer":42},true]
		val output = ujson.Arr(
			ujson.Obj("hello" -> ujson.Str("world"), "answer" -> ujson.Num(42)),
			ujson.Bool(true)
		)
		println("[generateJson] output: " + ujson.write(output))




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
		val json2 = ujson.Obj(
			"type"->ujson.Str("subscribe"),
			"product_ids"-> ujson.Arr("BTC-USD"),
			"channels"-> ujson.Arr("level2","heartbeat")
		
		)
		println("[generateJson] json2: " + ujson.write(json2))







		val test = ujson.write(json2)
		test



	}

	def startSimpleWebsocket: Websocket = {


		val jsonSubscribe = generateSubscribeJson
		println(s"[startSimpleWebsocket] json: ${jsonSubscribe}")


		// wss://ws-feed-public.sandbox.pro.coinbase.com


//		val cli:WebsocketClient[String] = WebsocketClient[String]("ws://echo.websocket.org") { wsMessage=>{
		val cli:WebsocketClient[String] = WebsocketClient[String]("wss://ws-feed-public.sandbox.pro.coinbase.com") { wsMessage=>{

			println(s"[WebsocketClient] websocket text: ${wsMessage}")

			// persist the message to the database
			println(s"[WebsocketClient] sending message to db")
			db ! DbActor.Msg( "[WebsocketActor.WsMessage] received: " + wsMessage)
			println(s"[WebsocketClient] sent message to db")
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
	final case class SayHello(message_text:String) extends Command
	final case class ShutItAllDown() extends Command

	// the type of message to be handled is declared to be of type SayHello
	// which means the message argument has the same type
	def apply(): Behavior[Command] = {
		println("[MainActor::apply] MainActor alive")

		// bootstrap the actor system
		Behaviors.setup({ context =>
			println("[MainActor.apply]")

			val dbActor = context.spawn(swimr.DbActor(), "dbActor")
			val wsActor = context.spawn(WebsocketActor(dbActor), "wsActor")
			val pingActor = context.spawn(PingActor(wsActor), "pinger")

			// pattern match not needed here because message will (even) implicitly
			// get the SayHello type ([T])
			Behaviors.receiveMessage(message => {

				message match {
					case m:SayHello =>
						println("[MainActor] sending 'start' to PingActor")
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

	myActorMain ! MainActor.SayHello("[Main] this is a message from Main to MainActor")

	 Thread.sleep(10000)
	 myActorMain ! MainActor.ShutItAllDown()


}
