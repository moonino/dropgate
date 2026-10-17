import { describe, expect, it } from "vitest";
import { decideLoginNotice, describeAuthUnavailable } from "./login-errors";

describe("decideLoginNotice", () => {
  it("오류 code가 없으면 안내를 띄우지 않는다", () => {
    expect(decideLoginNotice(undefined, undefined)).toEqual({ kind: "none" });
  });

  it("state 검증 실패는 요청 만료로 안내한다", () => {
    expect(decideLoginNotice("VALIDATION_FAILED", undefined)).toEqual({ kind: "error", message: "로그인 요청이 만료됐습니다. 다시 시도해 주세요." });
  });

  it("모르는 code는 일반 안내로 바꾼다", () => {
    expect(decideLoginNotice("SOMETHING_NEW", undefined)).toEqual({ kind: "error", message: "로그인하지 못했습니다. 다시 시도해 주세요." });
  });

  it("인증 서버가 응답하지 않으면 Retry-After 초 뒤 다시 시도하라고 안내한다", () => {
    expect(decideLoginNotice("AUTH_UNAVAILABLE", 2)).toEqual({ kind: "error", message: "로그인 서버가 잠시 응답하지 않습니다. 2초 뒤 다시 시도해 주세요." });
  });
});

describe("describeAuthUnavailable", () => {
  it("초를 모르면 잠시 뒤 다시 시도하라고 안내한다", () => {
    expect(describeAuthUnavailable(undefined)).toBe("로그인 서버가 잠시 응답하지 않습니다. 잠시 뒤 다시 시도해 주세요.");
  });
});
