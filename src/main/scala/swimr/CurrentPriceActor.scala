package swimr

import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import swimr.Model.Ticker

object CurrentPriceActor {
	sealed trait Command
	final case class Update(ticker:Ticker) extends Command
	final object Start extends Command
	final object Stop extends Command

	var cx:Option[Cancellable] = None
	var currentPrice:String = ""

	def apply():Behavior[Command] = {

		val messageBehavior = setupBehavior
		Behaviors.supervise(messageBehavior).onFailure(akka.actor.typed.SupervisorStrategy.restart)

	}

	def setupBehavior:Behavior[Command] = {

		Behaviors.receive ((context:ActorContext[CurrentPriceActor.Command], cmd:CurrentPriceActor.Command) => {
			cmd match {
				case Update(t) => {
					currentPrice = t.price
					Behaviors.same
				}

				case Start => {
					startPriceWatcher(context)
					Behaviors.same
				}

				case Stop => {
					cx match {
						case None => {
							println("[CurrentPriceActor] 'stop' received, but cx is None")
						}
						case Some(cancellable) => {
							println("[CurrentPriceActor] 'stop' received, canceling the cancellable")
							cancellable.cancel();
						}
					}
					Behaviors.same
				}
			}
		})
	}


	def startPriceWatcher(context: ActorContext[CurrentPriceActor.Command]) = {

		// import scala.concurrent.ExecutionContext.Implicits.global
		implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
		import scala.concurrent.duration.FiniteDuration
		import scala.concurrent.duration.MILLISECONDS

		cx = Some(context.system.scheduler.scheduleWithFixedDelay(
			FiniteDuration(0,MILLISECONDS),
			FiniteDuration(10000,MILLISECONDS))(
			new Runnable(){ def run() = {
				//					printf("\r[CurrentPriceActor] current: $%s", currentPrice)
				println("[CurrentPriceActor] current: $" + currentPrice)

			}
			})
		)
	}





}