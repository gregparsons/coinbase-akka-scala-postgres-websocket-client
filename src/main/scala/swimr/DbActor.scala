package swimr

import akka.actor.typed.{ActorRef, Behavior, PostStop, PreRestart, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import io.getquill.ast.Value
import io.getquill.{LowerCase, PostgresJdbcContext}
import org.postgresql.ds.PGSimpleDataSource
import swimr.Model.{Coinbase, L2update, Snapshot, Ticker}
import upickle.default

import scala.collection.mutable.ArrayBuffer


object DbActor {
	sealed trait Command

	case object Stop extends Command
	final case class Msg(m:String) extends Command
	final case class YouThere(parent:ActorRef[MainActor.Command]) extends Command
	object ConnectToPostgres extends Command

	var currentPriceActor: ActorRef[CurrentPriceActor.Command] = null
//	var conn:Option[PostgresJdbcContext[LowerCase.type]] = None
	var conn:Option[java.sql.Connection] = None

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
						conn match {
							case None => println("[DbActor] Json rcvd. Db not started.")
							case Some(conn) => {
								// process JSON, send to priceActor
								// TODO: make priceActor independent of this actor

								// println("[DbActor] rcvd: " + jsonStr)
								val coinbaseMsg = parseJson (jsonStr)
								coinbaseMsg match {
									case Some (t: Ticker) => {
										insertTicker(t)
										currentPriceActor ! CurrentPriceActor.Update (t)
									}

									case Some(s:Snapshot) => {
										???
									}

									case Some(l:L2update) => {
										???
									}

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

	def parseJson(jsonStr:String):Option[Coinbase] = {
//		println("[DbActor:parseJson] jsonStr: " + jsonStr)
		val jsonVal = ujson.read(jsonStr)
		// println("[DbActor:parseJson] jsonVal: " + jsonVal)
		//		try {
		val typ:String = jsonVal.obj("type").str;
		typ match {
			case "ticker" =>
				val time = jsonVal.obj("time").str
				val sequence = jsonVal.obj("sequence").num.toLong
				val product_id = jsonVal.obj("product_id").str
				val price = jsonVal.obj("price").str
				val best_bid = jsonVal.obj("best_bid").str
				val best_ask = jsonVal.obj("best_ask").str
				val side = jsonVal.obj("side").str
				val trade_id = jsonVal.obj("trade_id").num.toLong
				val last_size = jsonVal.obj("last_size").str
				val ticker = new Ticker(sequence = sequence, product_id = product_id, price = price, best_bid = best_bid, best_ask=best_ask, side=side, time=time, trade_id=trade_id, last_size =last_size )
				Some(ticker)

			case "snapshot" => {
//				println("[DbActor:parseJson] snapshot: " + jsonVal)
				val product_id = jsonVal.obj("product_id").str
				val bids:ArrayBuffer[ujson.Value] = jsonVal.obj("bids").arr



				val arrayOfBids = bids.map(v => {
					(v(0), v(1))

				})

				arrayOfBids foreach (println)

				val asks:ArrayBuffer[ujson.Value] = jsonVal.obj("asks").arr

//				implicit val snapshotRw = upickle.default.macroRW[Snapshot]
//				val snapshot = upickle.default.read[Snapshot](jsonVal)
//				Some(snapshot)
				None
			}

			case "l2update" => {
//				println("[DbActor:parseJson] l2update: " + jsonVal)
				val time = jsonVal.obj("time").str
				val product_id = jsonVal.obj("product_id").str
				val changes = jsonVal.obj("changes").arr
//				implicit val l2updateRw = upickle.default.macroRW[L2update]
//				val l2update: L2update = upickle.default.read[L2update](jsonVal)
//				Some(l2update)
				None
			}

			case "subscriptions"  => {
				println("[DbActor:parseJson] subscription: " + jsonVal)
				None
			}
			case "heartbeat" => {
				println("[DbActor:parseJson] heartbeat: " + jsonVal)
				None
			}

			case _ =>
				println("[DbActor:parseJson] NONE: " + jsonVal)
				None

		}
		//		} catch  {
		//			// case e:Throwable => None
		//			case e:Throwable => {
		//				println("[DbActor:parseJson] throwable: " + e)
		//				None
		//			}
		//		}
	}

	def insertTicker(cb:Coinbase) = {

		conn match {

			case Some(c) => {

				cb match {

					case t: Ticker => {
						//insert into t_cb_ticker(dtg, sequence, product_id, price, best_bid, best_ask, side, trade_id, last_size)
						//VALUES ('2021-04-21T21:21:52.314651Z',24019611415, 'BTC-USD', 54849.51,54849.51,54849.51,'buy',158978547,'0.00436093')


						val sql = s"insert into t_cb_ticker" +
							s"(dtg, sequence, product_id, price, best_bid, best_ask, side, trade_id, last_size) " +
							s"VALUES " +
							s"('${t.time}'," +
							s"${t.sequence}, " +
							s"'${t.product_id}', " +
							s"'${t.price}'," +
							s"'${t.best_bid}'," +
							s"'${t.best_ask}'," +
							s"'${t.side}'," +
							s"${t.trade_id}," +
							s"'${t.last_size}')"

						val stmt = c.prepareStatement(sql)
						val result = stmt.execute()
						// println("[insertTicker] sql insert result: " + result)

					}

					case _ => {}
				}
			}

			case None =>
				println("[insertTicker] conn is None, can't insert")

		}
	}


	def dbConnect: (Option[java.sql.Connection]) ={

		try{
			// TODO: changed to pooled?
			val pg = new PGSimpleDataSource
			pg.setUrl("jdbc:postgresql://10.1.1.205/coin")
			pg.setPortNumbers(Array(54320))
			pg.setUser("postgres")
			val conn = pg.getConnection
			Some(conn)
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
			FiniteDuration(5000,MILLISECONDS))(
				new Runnable(){ def run() = {
					//printf("\r[CurrentPriceActor] current: $%s", currentPrice)

					conn match {
						case None => {
							println("[DbActor.dbMonitor] conn before connect: " + conn )
							// attempt restart
							conn = dbConnect
							println("[DbActor.dbMonitor] conn after connect: " + conn )
						}

						case Some(dbCtx) => {
							// println("[DbActor.dbMonitor] conn: " + conn )
						}
					}
				}
			})
		)
	}

}

