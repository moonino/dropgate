import { describe, expect, it } from "vitest";
import { decideBuyButton, decideCountdownTarget } from "./purchase";

describe("decideBuyButton", () => {
  it("판매 중이면 구매 버튼을 연다", () => {
    expect(decideBuyButton({ status: "OPEN" }, "19:30", undefined)).toEqual({ kind: "enabled", label: "구매하기" });
  });

  it("오픈 전이면 오픈 시각을 적고 닫는다", () => {
    expect(decideBuyButton({ status: "SCHEDULED" }, "19:30", undefined)).toEqual({ kind: "disabled", label: "19:30 오픈" });
  });

  it("서버가 이미 구매했다고 답하면 그 상태로 닫는다", () => {
    const outcome = { kind: "rejected", code: "ALREADY_PURCHASED", message: "이미 구매했습니다" } as const;
    expect(decideBuyButton({ status: "OPEN" }, "19:30", outcome)).toEqual({ kind: "disabled", label: "이미 구매함" });
  });

  it("다시 시도할 수 있는 실패면 버튼을 열어 둔다", () => {
    const outcome = { kind: "rejected", code: "STOCK_UNAVAILABLE", message: "잠시 뒤 다시 시도해 주세요" } as const;
    expect(decideBuyButton({ status: "OPEN" }, "19:30", outcome)).toEqual({ kind: "enabled", label: "다시 시도" });
  });

  it("구매가 확정되면 다시 누를 수 없다", () => {
    const outcome = { kind: "confirmed", orderId: "o", confirmedAt: "2026-10-17T10:30:02.000Z" } as const;
    expect(decideBuyButton({ status: "OPEN" }, "19:30", outcome)).toEqual({ kind: "disabled", label: "구매 완료" });
  });
});

describe("decideCountdownTarget", () => {
  const drop = { openAt: "2026-10-17T10:30:00.000Z", closeAt: "2026-10-18T10:30:00.000Z" };

  it("오픈 전에는 오픈 시각까지 센다", () => {
    expect(decideCountdownTarget({ ...drop, status: "SCHEDULED" })).toEqual({ kind: "counting", label: "오픈까지", targetIso: drop.openAt, milestone: "오픈" });
  });

  it("판매 중에는 마감 시각까지 센다", () => {
    expect(decideCountdownTarget({ ...drop, status: "OPEN" })).toEqual({ kind: "counting", label: "마감까지", targetIso: drop.closeAt, milestone: "마감" });
  });

  it("마감한 드롭은 세지 않는다", () => {
    expect(decideCountdownTarget({ ...drop, status: "CLOSED" })).toEqual({ kind: "ended" });
  });
});
