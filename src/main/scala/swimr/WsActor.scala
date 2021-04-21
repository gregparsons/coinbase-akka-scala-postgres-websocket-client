package swimr

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.github.andyglow.websocket.{Websocket, WebsocketClient}

object WsActor {

	val COINBASE_URL_SANDBOX = "wss://ws-feed-public.sandbox.pro.coinbase.com"
	val COINBASE_URL = "wss://ws-feed.pro.coinbase.com"

	sealed trait Command
	case object Start extends Command
	case object Stop extends Command
	final case class YouThere(parent:ActorRef[MainActor.Command]) extends Command

	var db: ActorRef[DbActor.Command] =null
	var ws:Websocket = null

	def apply(dbActor: ActorRef[DbActor.Command]):Behavior[WsActor.Command] = {

		// TODO: what happens if the dbActor fails and restarts?
		// TODO: Send this as a message on dbActor startup?
		db = dbActor

		val messageBehavior = setupBehavior
		Behaviors.supervise(messageBehavior).onFailure(akka.actor.typed.SupervisorStrategy.restart)


	}

	def setupBehavior():Behavior[Command] = {
		Behaviors.receive( (context, message) => {
			message match {

				case Start => {
					ws = startSimpleWebsocket
					Behaviors.same
				}

				case YouThere(parent) => {
					println("[WsActor] Got YouThere. Yeah, I'm Here")
					parent ! MainActor.WsStarted
					Behaviors.same
				}

				case Stop => {
					Behaviors.stopped
				}
			}
		})
	}

	def startSimpleWebsocket: Websocket = {

		val jsonSubscribe = generateSubscribeJson
		//println(s"[startSimpleWebsocket] json: ${jsonSubscribe}")

		val cli:WebsocketClient[String] = WebsocketClient[String](COINBASE_URL) { wsMessage=>{
			// println("[startSimpleWebsocket] rcvd: " + wsMessage)
			db ! DbActor.Msg(wsMessage)
		}}
		val ws = cli.open()

		// send coinbase's subscribe message per:
		// https://docs.pro.coinbase.com/#protocol-overview
		ws ! jsonSubscribe

		ws
	}

	def generateSubscribeJson: String = {
		val subscribeJson = ujson.Obj(
			"type"->ujson.Str("subscribe"),
			"product_ids"-> ujson.Arr("BTC-USD"),
			"channels"-> ujson.Arr(/*"level2","heartbeat", */"ticker")
		)
		println("[generateJson] subscribe: " + ujson.write(subscribeJson))
		ujson.write(subscribeJson)
	}

}
