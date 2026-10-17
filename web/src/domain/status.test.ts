import { describe, expect, it } from "vitest";
import { DROP_STATUS_BADGES, formatStock } from "./status";

describe("formatStock", () => {
  it("남은 수량이 null이면 집계 전으로 보여 준다", () => {
    expect(formatStock({ remainingQuantity: null, totalQuantity: 100 })).toBe("집계 전");
  });

  it("남은 수량이 있으면 남은 수와 총수량을 함께 보여 준다", () => {
    expect(formatStock({ remainingQuantity: 37, totalQuantity: 100 })).toBe("37 / 100");
  });
});

describe("DROP_STATUS_BADGES", () => {
  it("상태마다 문구와 모양이 모두 다르다", () => {
    const badges = Object.values(DROP_STATUS_BADGES);
    expect(new Set(badges.map((badge) => badge.label)).size).toBe(badges.length);
    expect(new Set(badges.map((badge) => badge.tone)).size).toBe(badges.length);
  });
});
