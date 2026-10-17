package dropgate.common.error

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

private const val RETRY_AFTER_SECONDS = "2"

enum class ErrorCode(val status: HttpStatus, val message: String) {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
    NOT_ADMITTED(HttpStatus.FORBIDDEN, "입장 순서가 아니거나 입장 시간이 지났습니다"),
    DROP_NOT_FOUND(HttpStatus.NOT_FOUND, "드롭을 찾을 수 없습니다"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다"),
    JOB_EXECUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "배치 실행 기록을 찾을 수 없습니다"),
    DROP_NOT_OPEN(HttpStatus.CONFLICT, "판매 중인 드롭이 아닙니다"),
    SOLD_OUT(HttpStatus.CONFLICT, "재고가 모두 소진됐습니다"),
    ALREADY_PURCHASED(HttpStatus.CONFLICT, "이미 구매한 드롭입니다"),
    ORDER_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 취소한 주문입니다"),
    DROP_CLOSED(HttpStatus.CONFLICT, "마감된 드롭의 주문은 취소할 수 없습니다"),
    JOB_ALREADY_RUNNING(HttpStatus.CONFLICT, "같은 잡이 이미 실행 중입니다"),
    KAKAO_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "카카오 로그인 응답을 받지 못했습니다"),
    AUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "인증을 확인할 수 없습니다. 잠시 뒤 다시 시도해 주세요"),
    STOCK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "재고를 확인할 수 없습니다. 잠시 뒤 다시 시도해 주세요"),
    ORDER_PERSIST_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "주문을 저장하지 못했습니다. 잠시 뒤 다시 시도해 주세요"),
    NOTIFICATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "알림을 불러올 수 없습니다. 잠시 뒤 다시 시도해 주세요"),
    ;

    fun createHeaders(): HttpHeaders = HttpHeaders().apply {
        if (status == HttpStatus.SERVICE_UNAVAILABLE) set(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
    }
}
