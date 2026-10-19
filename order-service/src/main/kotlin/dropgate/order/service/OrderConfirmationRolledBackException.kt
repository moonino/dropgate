package dropgate.order.service

import dropgate.common.error.ErrorCode
import java.util.UUID

class OrderConfirmationRolledBackException(
    val errorCode: ErrorCode,
    dropId: UUID,
    userId: UUID,
    cause: RuntimeException,
) : RuntimeException("드롭 $dropId 사용자 $userId 의 주문 확정 본문이 실패했습니다", cause)
