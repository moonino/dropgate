import { describe, expect, it } from "vitest";
import { convertKstInputToUtcIso, formatKstDay, formatKstStamp, formatKstTime, splitRemainingTime } from "./time";

describe("한국 시간 표시", () => {
  it("UTC 시각을 한국 시각으로 보여 준다", () => {
    expect(formatKstTime("2026-10-17T10:30:00.000Z")).toBe("19:30");
  });

  it("날짜는 월, 일, 요일로 보여 준다", () => {
    expect(formatKstDay("2026-10-17T10:30:00.000Z")).toBe("10월 17일 (토)");
  });

  it("주문 시각은 초까지 보여 준다", () => {
    expect(formatKstStamp("2026-10-17T10:30:02.000Z")).toBe("10.17 19:30:02");
  });
});

describe("convertKstInputToUtcIso", () => {
  it("입력란의 한국 시각을 UTC로 바꾼다", () => {
    expect(convertKstInputToUtcIso("2026-10-20T19:00")).toBe("2026-10-20T10:00:00.000Z");
  });

  it("시각 형식이 아니면 예외를 던진다", () => {
    expect(() => convertKstInputToUtcIso("내일 저녁")).toThrow(RangeError);
  });
});

describe("splitRemainingTime", () => {
  it("남은 시간을 시, 분, 초 두 자리로 나눈다", () => {
    const now = Date.parse("2026-10-17T08:45:23.000Z");
    expect(splitRemainingTime("2026-10-17T10:30:00.000Z", now)).toEqual({ hours: "01", minutes: "44", seconds: "37", finished: false });
  });

  it("목표 시각이 지나면 0으로 멈추고 끝났다고 알린다", () => {
    const now = Date.parse("2026-10-17T10:30:05.000Z");
    expect(splitRemainingTime("2026-10-17T10:30:00.000Z", now)).toEqual({ hours: "00", minutes: "00", seconds: "00", finished: true });
  });
});
