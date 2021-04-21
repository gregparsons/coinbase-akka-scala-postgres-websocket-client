package swimr

import akka.actor.typed.{ActorRef, Behavior, PostStop, PreRestart, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import io.getquill.{LowerCase, PostgresJdbcContext}
import org.postgresql.ds.PGSimpleDataSource
import swimr.Model.Ticker


object DbActor {
	sealed trait Command

	case object Stop extends Command
	final case class Msg(m:String) extends Command
	final case class YouThere(parent:ActorRef[MainActor.Command]) extends Command
	object ConnectToPostgres extends Command

	var currentPriceActor: ActorRef[CurrentPriceActor.Command] = null
	var dbContext:Option[PostgresJdbcContext[LowerCase.type]] = None


	def apply(currPriceActor: ActorRef[CurrentPriceActor.Command]):Behavior[Command] = {

		println("[DbActor:apply]")

		currentPriceActor = currPriceActor

		val behavior = setupBehavior
		val behavior2 = Behaviors.supervise(behavior).onFailure(akka.actor.typed.SupervisorStrategy.restart)


		behavior2

	}

	def setupBehavior:Behavior[Command] = {

		Behaviors.supervise[DbActor.Command] {
			Behaviors.receive((context: ActorContext[DbActor.Command], msg: DbActor.Command) => {



				msg match {

					case ConnectToPostgres => {
						dbMonitor(context)
						Behaviors.same
					}

					case YouThere(parent) => {
						println("[DbActor] Yeah, I'm here.")
						parent ! MainActor.DbStarted
						Behaviors.same
					}

					case Msg(jsonStr) => {

						dbContext match {
							case None => println("[DbActor] Json rcvd. Db not started.")
							case Some(dbContext) => {
								// process JSON, send to priceActor
								// TODO: make priceActor independent of this actor

								// println("[DbActor] rcvd: " + jsonStr)
								val ticker = parseJsonTicker (jsonStr)
								ticker match {
									case Some (t: Ticker) => currentPriceActor ! CurrentPriceActor.Update (t)
									case None => { }
								}
							}
						}

						Behaviors.same
					}


					case Stop => {
						???

					}
				}
			})

			// restart the child
		}.onFailure(SupervisorStrategy.restart.withStopChildren(false))
//		.receiveSignal{
//			case (_, signal) if signal == PreRestart || signal == PostStop =>
//				println("[DbActor] received signal: " + signal)
//				Behaviors.same
//		}
	}

	def dbConnect: (Option[PostgresJdbcContext[LowerCase.type]]) ={
		// "let it fail"
		try{
			import io.getquill._
			// TODO: changed to pooled?
			val pgDatasource = new PGSimpleDataSource
			pgDatasource.setUrl("jdbc:postgresql://10.1.1.205/coin")
			pgDatasource.setPortNumbers(Array(54320))
			pgDatasource.setUser("postgres")
			import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
			val config = new HikariConfig()
			config.setDataSource(pgDatasource)


			val ctx = new PostgresJdbcContext(LowerCase, new HikariDataSource(config))
			import ctx._
			Some(ctx)

		} catch {
			case e:Throwable => {
				println("[DbActor:dbConnect] throwable: " + e)
				None

			}
		}
	}

	def dbMonitor(context: ActorContext[DbActor.Command]) = {

		// routinely check if db is alive; try again if it's not, do nothing if it is

		// timer
		// import scala.concurrent.ExecutionContext.Implicits.global
		implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
		import scala.concurrent.duration.FiniteDuration
		import scala.concurrent.duration.MILLISECONDS

		val cx = Some(context.system.scheduler.scheduleWithFixedDelay(
			FiniteDuration(0,MILLISECONDS),
			FiniteDuration(10000,MILLISECONDS))(
				new Runnable(){ def run() = {
					//printf("\r[CurrentPriceActor] current: $%s", currentPrice)



					dbContext match {
						case None => {
							println("[DbActor.dbMonitor] dbContext before connect: " + dbContext )
							// TODO: attempt restart
							dbContext = dbConnect
							println("[DbActor.dbMonitor] dbContext after connect: " + dbContext )
						}

						case Some(dbCtx) => {
							println("[DbActor.dbMonitor] dbContext: " + dbContext )
						}



					}

				}
			})
		)
	}

	def parseJsonTicker(jsonStr:String):Option[Ticker] = {
		// println("[DbActor:parseJsonTicker] jsonStr: " + jsonStr)
		val jsonVal = ujson.read(jsonStr)
		// println("[DbActor:parseJsonTicker] jsonVal: " + jsonVal)
		try {
			val typ:String = jsonVal.obj("type").str;
			// println("[DbActor:parseJsonTicker] typ: " + typ)
			typ match {

				case "ticker" =>
					println("[DbActor:parseJsonTicker] ticker: " + jsonVal)
					implicit val tickerRW = upickle.default.macroRW[Ticker]
					val ticker = upickle.default.read[Ticker](jsonVal)
					println("[DbActor:parseJsonTicker] ticker: " + ticker)
					val price = jsonVal.obj("price").str
					// println(s"[DbActor:parseJsonTicker] price: " + price)
					//Some(price)
					Some(ticker)

				case _ =>
					println("[DbActor:parseJsonTicker] NONE: " + jsonVal.str)
					None

			}
		} catch  {
			// case e:Throwable => None
			case e:Throwable => {
				println("[DbActor:parseJsonTicker] throwable: " + e)
				None
			}
		}
	}
}

