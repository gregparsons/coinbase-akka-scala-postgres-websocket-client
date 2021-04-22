package swimr

object Model {

	// https://docs.pro.coinbase.com/#the-ticker-channel

	sealed trait Coinbase

	case class Ticker (
		time:String,
		sequence:Long,
		product_id:String,
		price:String,
		best_bid:String,
		best_ask:String,
		side:String,
		trade_id:Long,
		last_size:String

	) extends Coinbase

	case class L2snapshot(product_id:String, bids:Array[(String, String)], asks: Array[(String, String)]) extends Coinbase

	case class L2update (product_id:String, time:String, changes:Array[(String, String, String)]) extends Coinbase


}
