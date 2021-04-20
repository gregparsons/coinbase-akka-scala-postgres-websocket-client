package swimr

import akka.actor.Cancellable
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

object DbActor {
	sealed trait Command
	case object Start extends Command
	case object Stop extends Command
	final case class Msg(m:String) extends Command

	var count = 0

	def apply(currentPriceActor: ActorRef[CurrentPriceActor.Command]):Behavior[Command] = {
		println("[DbActor:apply]")

		Behaviors.receive{ (context, cmd:Command) => {
			cmd match {

				case Start|Stop => {
					println("[DbActor] starting/stopping")

				}

				case Msg(jsonStr) => {
					//println(s"[DbActor] received: ${m}, context: ${context}")
					//println("[DbActor] starting long db action " + count)
					//Thread.sleep(500)
					//println("[DbActor] done with long db action " + count)
					// count+=1

					parseJsonTicker(jsonStr) match {
						case Some(price) => {
							currentPriceActor ! CurrentPriceActor.Update(price)

						}
						case None => { }
					}


				}
			}
			Behaviors.same
		}}
	}

	def parseJsonTicker(jsonStr:String):Option[String] = {

		val jsonVal = ujson.read(jsonStr)


		// println(s"[parseJsonTicker] coinbaseType: " + jsonVal.obj("type"))

		try {
			val typ = jsonVal.obj("type");
			typ.str match {
				case "ticker" => {
					val price = jsonVal.obj("price").str
					// println(s"[parseJsonTicker] price: " + price)
					Some(price)
				}
				case _ => None
			}
		} catch  {
			// case e:Throwable => None
			case _:Throwable => None
		}
	}
}

object CurrentPriceActor {
	sealed trait Command
	final case class Update(m:String) extends Command
	final object Start extends Command
	final object Stop extends Command


	var cx:Option[Cancellable] = None

	var currentPrice:String = ""

	def apply():Behavior[Command] = {
		println("[CurrentPriceActor:apply]")

		Behaviors.receive{ (context, cmd:Command) => {
			cmd match {
				case Update(m) => {

					currentPrice = m

				}

				case Start => {
					startPriceWatcher(context)
				}

				case Stop =>{
					cx match {
						case None => {
							println("[CurrentPriceActor] 'stop' received, but cx is None")
						}
						case Some(cancellable) => {
							println("[CurrentPriceActor] 'stop' received, canceling the cancellable")
							cancellable.cancel();
						}
					}
				}

			}
			Behaviors.same
		}}
	}


	def startPriceWatcher(context: ActorContext[Command]) = {
		import scala.concurrent.ExecutionContext.Implicits.global
		import scala.concurrent.duration.FiniteDuration
		import scala.concurrent.duration.MILLISECONDS

		cx = Some(context.system.scheduler.scheduleWithFixedDelay(
			FiniteDuration(0,MILLISECONDS),
			FiniteDuration(10,MILLISECONDS))(
				new Runnable(){ def run() = {

					printf("\r[CurrentPriceActor] current: $%s", currentPrice)
					//wsActor ! WebsocketActor.Ping(context.self)
				}
			})
		)
	}





}