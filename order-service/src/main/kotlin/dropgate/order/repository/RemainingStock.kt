package dropgate.order.repository

sealed interface RemainingStock {
    data class Known(val quantity: Int) : RemainingStock
    data object Unknown : RemainingStock
}
