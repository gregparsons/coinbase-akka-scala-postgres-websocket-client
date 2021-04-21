package swimr

object Model {

	case class Ticker (
		sequence:Long,
		product_id:String,
		price:String,
		best_bid:String,
		best_ask:String,
		side:String,
		time:String,
		trade_id:Long,
		last_size:String

	)


}
