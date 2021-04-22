package swimr

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.github.andyglow.websocket._

import java.util.Calendar

object WsActor {

	val COINBASE_URL_SANDBOX = "wss://ws-feed-public.sandbox.pro.coinbase.com"
	val COINBASE_URL = "wss://ws-feed.pro.coinbase.com"

	sealed trait Command
	case object Start extends Command
	case object Stop extends Command
	final case class YouThere(parent:ActorRef[MainActor.Command]) extends Command

	var db: ActorRef[DbActor.Command] =null
	var ws:Option[Websocket] = None
	var timeOfLast = System.currentTimeMillis

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
					// ws = startSimpleWebsocket
					startWsMonitor(context)
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

	def startSimpleWebsocket: Option[Websocket] = {
		try{
			val jsonSubscribe = generateSubscribeJson
			val client = {
				val builder = WebsocketClient.Builder[String](COINBASE_URL) { wsMessage => {
					// println("[startSimpleWebsocket] rcvd: " + wsMessage)

					timeOfLast = System.currentTimeMillis

					db ! DbActor.Msg(wsMessage)
					}
				} onFailure {
					case ex: Throwable  =>
						println(s"[startSimpleWebsocket] Error occurred.", ex)
						ws = None
				} onClose {
					println(s"[startSimpleWebsocket] connection closed");
					ws = None
				}
				builder.build()
			}

			val websocket = client.open()

			println("[WsActor.startSimpleWs] ws: " + ws)
			// send coinbase's subscribe message per:
			// https://docs.pro.coinbase.com/#protocol-overview
			websocket ! jsonSubscribe

			Some(websocket)

		} catch {
			case e:Throwable => {
				println("[DbActor:dbConnect] throwable: " + e)
				None
			}
		}
	}

	def generateSubscribeJson: String = {
		val subscribeJson = ujson.Obj(
			"type"->ujson.Str("subscribe"),
			"product_ids"-> ujson.Arr("BTC-USD"),
			"channels"-> ujson.Arr(/*"level2","heartbeat",*/"ticker")
		)
		println("[generateJson] subscribe: " + ujson.write(subscribeJson))
		ujson.write(subscribeJson)
	}

	def startWsMonitor(context: ActorContext[WsActor.Command]) = {

		// TODO: restart if nothing has been received for some time
		// routinely check if db is alive; try again if it's not, do nothing if it is
		// timer
		// import scala.concurrent.ExecutionContext.Implicits.global
		implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
		import scala.concurrent.duration.FiniteDuration
		import scala.concurrent.duration.MILLISECONDS

		context.system.scheduler.scheduleWithFixedDelay(FiniteDuration(0,MILLISECONDS), FiniteDuration(5000,MILLISECONDS))(

			new Runnable(){
				def run() = {
					ws match {
						case None => {
							println("[WsActor.wsMonitor] ws before connect: " + ws)
							// attempt restart
							ws = startSimpleWebsocket
							println("[WsActor.wsMonitor] ws after connect: " + ws)
						}
						case Some(_) =>{
							val timeSince = System.currentTimeMillis - timeOfLast
							if (timeSince > 10000) {
								println("s[WsActor] It's been " + timeSince + "ms. Restarting websocket.")
								ws = None
								println("[WsActor.wsMonitor] ws before connect: " + ws)
								ws = startSimpleWebsocket
								println("[WsActor.wsMonitor] ws after connect: " + ws)
							}
						}
					}
				}
			}
		)
	}
}