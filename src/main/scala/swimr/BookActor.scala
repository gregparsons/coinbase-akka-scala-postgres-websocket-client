package swimr

import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import swimr.Model.{Coinbase, L2update, L2snapshot, Ticker}

import scala.collection.mutable

object BookActor {

	val WATCHER_INTERVAL = 400 // milliseconds

	// akka messages
	sealed trait Command
	final object Start extends Command
	final object Stop extends Command
	final case class CoinbaseMsg(cb: Model.Coinbase) extends Command

	var cx:Option[Cancellable] = None

	// coinbase state

	// book
	type LB = mutable.TreeMap[Double, Double]
	var sb = new LB // sell book
	var bb = new LB // buy book

	var currentTicker:Option[Ticker] = None
	var tickers = mutable.ArrayDeque[Ticker]()



	def apply():Behavior[Command] = {

		val messageBehavior = setupBehavior
		Behaviors.supervise(messageBehavior).onFailure(akka.actor.typed.SupervisorStrategy.restart)

	}

	def setupBehavior:Behavior[Command] = {

		Behaviors.receive ((context:ActorContext[BookActor.Command], cmd:BookActor.Command) => {

			cmd match {

				case CoinbaseMsg(cbMessage) => {

					cbMessage match {

						case t:Model.Ticker => {
							currentTicker = Some(t)
							tickers.prepend(t)

							assert(tickers.head == t)

							cleanTickers

							// currentPrice = t.price
							Behaviors.same
						}

						case snap:Model.L2snapshot => {
							println("[CurrentBookActor] BookSnapshotMsg")
							// TODO: load up the book

							storeLocalBookSnapshot(snap)

							Behaviors.same
						}

						case bookUpdate:Model.L2update => {
							storeLocalBookUpdate(bookUpdate)
							Behaviors.same
						}
					}
				}

				case Start => {
					bookWatcher(context)
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

	def storeLocalBookSnapshot(snapshot: L2snapshot) = {

		// TODO: should this clear out the local book? yes I guess

		sb.clear
		bb.clear
		println("[BookActor.watcher] bb: " + bb.size + ", total: $" + bookTotalValueDollars(bb))
		println("[BookActor.watcher] sb: " + sb.size+ ", total: $" + bookTotalValueDollars(sb))


		// same as below
//		snapshot.bids foreach (x=>{
//			val price = x._1.toDouble // price
//			val qty = x._2.toDouble // qty
//			bb.addOne(price, qty)
//		})
//
//		snapshot.asks foreach (x=>{
//			val price = x._1.toDouble // price
//			val qty = x._2.toDouble // qty
//			sb.addOne(price, qty)
//		})


		// offers to buy
		val allBids:Array[(Double, Double)] = snapshot.bids.map( x => (x._1.toDouble, x._2.toDouble) )
		bb.addAll(allBids)



		// offers to sell
		val allAsks:Array[(Double, Double)] = snapshot.asks.map( x => (x._1.toDouble, x._2.toDouble) )
		sb.addAll(allAsks)

	}

	def storeLocalBookUpdate(bookUpdate:L2update) = {
		bookUpdate.product_id match {

			case "BTC-USD" => {
				bookUpdate.changes foreach (
					update => {
						/*
						(sell,54680.00,5.00000000)
						(sell,54640.89,0.09100000)
						(buy,54610.85,0.00000000)
						(buy,54450.03,0.01000000)
					 */
						val priceKey = update._2.toDouble
						val qtyVal = update._3.toDouble
						update._1 match {
							case "buy" =>
								if (qtyVal == 0.0) bb.remove(priceKey)
								else bb.put(priceKey, qtyVal)
							case "sell" =>
								if (qtyVal == 0.0) sb.remove(priceKey)
								else sb.put(priceKey, qtyVal)
						}
					})
			}
			case _ => {
				// TODO: all other currencies
			}
		}
	}

	def bookWatcher(context: ActorContext[BookActor.Command]) = {

		// import scala.concurrent.ExecutionContext.Implicits.global
		implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
		import scala.concurrent.duration.FiniteDuration
		import scala.concurrent.duration.MILLISECONDS

		cx = Some(context.system.scheduler.scheduleWithFixedDelay(
			FiniteDuration(0,MILLISECONDS),
			FiniteDuration(WATCHER_INTERVAL,MILLISECONDS))(
			new Runnable(){ def run() = {
				//					printf("\r[CurrentPriceActor] current: $%s", currentPrice)
//				println("[CurrentPriceActor] current: $" + currentPrice)
				if (tickers.size > 0) {
//					case Some(t) =>
//						printf("\r[CurrentPriceActor] ticker: $%s", t)


						val t = tickers.head

						println("[BookActor.watcher] ticker time: " + t.time + ", price: " + t.price)
						println("[BookActor.watcher] bb: " + bb.size +
							", USD: $" + bookTotalValueDollars(bb) +
							", BTC: " + bookTotalBtc(bb) +
							", Avg: $" + bookTotalValueDollars(bb)/bookTotalBtc(bb) +
							", Ticker: $" + t.price +
							", Max: " + bb.max +
							", Min: " + bb.min
						)
						println("[BookActor.watcher] sb: " + sb.size+
							", USD: $" + bookTotalValueDollars(sb) +
							", BTC: " + bookTotalBtc(sb) +
							", Avg: $" + bookTotalValueDollars(sb)/bookTotalBtc(sb) +
							", Ticker: $" + t.price +
							", Max: " + sb.max +
							", Min: " + sb.min
						)
						println("[BookActor] spread: " + (sb.min._1 - bb.max._1))
						println("[BookActor] price ema004: " + movingAverageTickerPrice(tickers.toSeq, 4) + "\t\t\tprice diff: " + (movingAverageTickerPrice(tickers.toSeq, 4) - tickers.head.price.toDouble))
						println("[BookActor] price ema008: " + movingAverageTickerPrice(tickers.toSeq, 8)+ "\t\t\tprice diff: " + (movingAverageTickerPrice(tickers.toSeq, 8) - tickers.head.price.toDouble))
						println("[BookActor] price ema012: " + movingAverageTickerPrice(tickers.toSeq, 12)+ "\t\t\tprice diff: " + (movingAverageTickerPrice(tickers.toSeq, 12) - tickers.head.price.toDouble))
						println("[BookActor] price ema020: " + movingAverageTickerPrice(tickers.toSeq, 20)+ "\t\t\tprice diff: " + (movingAverageTickerPrice(tickers.toSeq, 20) - tickers.head.price.toDouble))
						println("[BookActor] price ema100: " + movingAverageTickerPrice(tickers.toSeq, 100)+ "\t\t\tprice diff: " + (movingAverageTickerPrice(tickers.toSeq, 100) - tickers.head.price.toDouble))


//					case _ => { }
				}

			}
			})
		)
	}

	/**
	 * Return the total value of all the bids/asks in this book (lb = "local book").
	 */
	def bookTotalValueDollars(lb:LB):Double = {
		lb.map(x =>{x._1 * x._2}).reduceOption(_ + _) match {
			case None => 0.0
			case Some(total:Double) => total
		}
	}

	/**
	 * Sum the quantity of BTC on the book
	 */
	def bookTotalBtc(lb:LB):Double = {
		lb.map(_._2).reduceOption(_+_) match {
			case None => 0.0
			case Some(total) => total
		}
	}

	def movingAverageTickerPrice(arr:Seq[Ticker], scope:Int):Double = {
		val i = 0
		// get first n (or fewer) prices
		val arrayToAvg = arr.take(scope)
		val count = arrayToAvg.size
		val sum = arrayToAvg.map(x=> x.price.toDouble).reduce(_+_)
		sum / count.toDouble
	}

	def cleanTickers = {
		if (tickers.size> 200){
			// trim to the top 100
			tickers = tickers.take(100)
		}
	}

//	def movingAverageTickerPrice(arr:Seq[Double], scope:Int):Double = {
//		val i = 0
//		// get first n (or fewer) prices
//		val arrayToAvg = arr.take(scope)
//		val count = arrayToAvg.size
//		val sum = arrayToAvg.reduce(_+_)
//		sum / count.toDouble
//	}


}
/*


[BookActor.watcher] ticker: Ticker(2021-04-22T19:19:09.389156Z,24060131357,BTC-USD,52369.22,52363.56,52369.22,buy,159483349,0.0012)
[BookActor.watcher] bb: 13619, USD: $1.8520294378943905E8, BTC: $3564615.7334290473
[BookActor.watcher] sb: 15088, USD: $2.3561508013951607E9, BTC: $6631.549564580719




*/