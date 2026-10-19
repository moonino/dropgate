package dropgate.order.repository

import dropgate.common.error.ErrorCode

sealed interface StockReservation {
    data class Reserved(val remainingQuantity: Int) : StockReservation
    data class Rejected(val errorCode: ErrorCode) : StockReservation
}
