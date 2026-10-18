import { describe, expect, it } from "vitest";
import { appendRetryAfter, describeRetryAfter, parseRetryAfterSeconds } from "./retry";

describe("parseRetryAfterSeconds", () => {
  it("양의 정수 초를 숫자로 읽는다", () => {
    expect(parseRetryAfterSeconds("2")).toBe(2);
  });

  it("비었거나 정수 초가 아니면 초를 모른다고 본다", () => {
    expect([null, undefined, "", "0", "-1", "soon"].map(parseRetryAfterSeconds)).toEqual([undefined, undefined, undefined, undefined, undefined, undefined]);
  });
});

describe("describeRetryAfter", () => {
  it("초를 알면 그 초 뒤 다시 시도하라고 안내한다", () => {
    expect(describeRetryAfter(2)).toBe("2초 뒤 다시 시도해 주세요.");
  });

  it("초를 모르면 잠시 뒤 다시 시도하라고 안내한다", () => {
    expect(describeRetryAfter(undefined)).toBe("잠시 뒤 다시 시도해 주세요.");
  });
});

describe("appendRetryAfter", () => {
  it("서버 문구 뒤에 다시 시도할 초를 붙인다", () => {
    expect(appendRetryAfter("재고 서버가 바쁩니다.", 3)).toBe("재고 서버가 바쁩니다. 3초 뒤 다시 시도해 주세요.");
  });

  it("초를 모르면 서버 문구를 그대로 둔다", () => {
    expect(appendRetryAfter("재고 서버가 바쁩니다.", undefined)).toBe("재고 서버가 바쁩니다.");
  });
});
