import { describe, expect, it } from "vitest";
import { decideCancelControl } from "./cancel";

describe("decideCancelControl", () => {
  it("확정 주문에는 취소 버튼을 둔다", () => {
    expect(decideCancelControl({ status: "CONFIRMED" }, undefined)).toEqual({ kind: "button" });
  });

  it("취소한 주문은 다시 살 수 없다고 안내한다", () => {
    expect(decideCancelControl({ status: "CANCELED" }, undefined)).toEqual({ kind: "note", text: "다시 살 수 없습니다" });
  });

  it("마감 뒤라 거절되면 버튼을 비활성화하고 서버 문구를 함께 둔다", () => {
    const rejection = { kind: "rejected", code: "DROP_CLOSED", message: "마감한 드롭의 주문은 취소할 수 없습니다", retryAfterSeconds: undefined } as const;
    expect(decideCancelControl({ status: "CONFIRMED" }, rejection)).toEqual({ kind: "disabled", message: "마감한 드롭의 주문은 취소할 수 없습니다" });
  });

  it("이미 취소된 주문이라 거절되면 버튼을 비활성화하고 서버 문구를 함께 둔다", () => {
    const rejection = { kind: "rejected", code: "ORDER_ALREADY_CANCELED", message: "이미 취소한 주문입니다", retryAfterSeconds: undefined } as const;
    expect(decideCancelControl({ status: "CONFIRMED" }, rejection)).toEqual({ kind: "disabled", message: "이미 취소한 주문입니다" });
  });

  it("남의 주문이라 거절되면 버튼 대신 서버 문구를 보여 준다", () => {
    const rejection = { kind: "rejected", code: "FORBIDDEN", message: "본인 주문만 취소할 수 있습니다", retryAfterSeconds: undefined } as const;
    expect(decideCancelControl({ status: "CONFIRMED" }, rejection)).toEqual({ kind: "note", text: "본인 주문만 취소할 수 있습니다" });
  });

  it("일시적인 실패면 버튼을 남겨 다시 시도하게 한다", () => {
    const rejection = { kind: "rejected", code: "STOCK_UNAVAILABLE", message: "잠시 뒤 다시", retryAfterSeconds: undefined } as const;
    expect(decideCancelControl({ status: "CONFIRMED" }, rejection)).toEqual({ kind: "button" });
  });
});
