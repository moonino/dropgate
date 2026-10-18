# 취소의 두 UPDATE 순서가 교착과 무관하다는 지적을 따르지 않는다

| 항목 | 내용 |
|---|---|
| 날짜, 도구 | 2026-10-18, Claude Code 자동 리뷰 지적 |
| 제안 요지 | FOR UPDATE 잠금은 트랜잭션 끝까지 유지되므로 취소의 두 UPDATE 순서를 바꿔도 잠금 순서는 바뀌지 않고, 구매와 순서를 맞추려면 drops 행을 `SELECT ... FOR UPDATE`로 먼저 잠근다. |
| 왜 틀렸나 | 동시성 가정 오류. 지적은 교착이 행 잠금을 잡는 순서로만 정해진다고 가정했다. 구매의 INSERT ON CONFLICT 유일성 검사는 FOR UPDATE로 잠기기만 한 orders 행은 기다리지 않고, 끝나지 않은 트랜잭션이 갱신한 행만 기다린다. 그래서 OrderCancelService.cancel에서 주문 상태 UPDATE(order.cancel)를 재고 복구 UPDATE(restoreOneUnitAndReopen)보다 먼저 하면 같은 사용자의 취소와 재구매가 서로를 기다려 교착이 난다. 교착을 막는 것은 잠금 순서가 아니라 갱신 순서이므로, 잠금 순서를 맞추려고 drops 행을 먼저 잠그자는 방법도 같은 가정 위에 있어 쓰지 않았다. |
| 어떻게 알았나 | 실행. [리뷰 지적](https://github.com/moonino/dropgate/pull/82#discussion_r4302480659)의 두 UPDATE 순서 바꾸기를 #83 브랜치(feat/9-order-cancel)에서 OrderCancelService.cancel의 restoreOneUnitAndReopen 줄과 order.cancel 줄을 맞바꿔 `./gradlew :order-service:test --tests dropgate.order.OrderCancelTest`로 돌렸다(로컬 Docker Testcontainers PostgreSQL). `같은 사용자가 취소와 재구매를 동시에 보내도 취소는 200, 재구매는 409 ALREADY_PURCHASED만 돌려준다` 테스트가 cancel 500, purchase 500으로 실패했고 로그에 40P01 deadlock detected가 남았다. 지금 순서로는 OrderCancelTest 15개가 모두 통과했다. 결과는 [답글](https://github.com/moonino/dropgate/pull/82#discussion_r4306648321)과 커밋 `docs: 리뷰 지적을 반영한다 (#9)` 본문에 남겼다. 아래 블록은 같은 재현을 명령으로 옮긴 것이고, perl은 두 줄만 맞바꾼다. drops 행을 먼저 잠그는 방법은 따로 실행하지 않았다. 성능 측정이나 합성 부하 시험은 아니다. |
| 대신 한 것 | 지적의 문서 불일치는 반영해 08 4절과 ADR 0019 결과 절에 잠금 순서 대신 주문 상태 UPDATE를 재고 복구 UPDATE 뒤에 두는 이유를 적고, 코드는 그대로 둔다. PR #82 |

```sh
perl -0pi -e 's/^(\s+drops\.restoreOneUnitAndReopen\(drop\.id\)\n)(\s+order\.cancel\(canceledAt\)\n)/$2$1/m' order-service/src/main/kotlin/dropgate/order/service/OrderCancelService.kt
./gradlew :order-service:test --tests dropgate.order.OrderCancelTest
git checkout -- order-service/src/main/kotlin/dropgate/order/service/OrderCancelService.kt
./gradlew :order-service:test --tests dropgate.order.OrderCancelTest
```
