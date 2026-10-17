import { describe, expect, it } from "vitest";
import { decideLoginNotice } from "./login-errors";

describe("decideLoginNotice", () => {
  it("오류 code가 없으면 안내를 띄우지 않는다", () => {
    expect(decideLoginNotice(undefined)).toEqual({ kind: "none" });
  });

  it("state 검증 실패는 요청 만료로 안내한다", () => {
    expect(decideLoginNotice("VALIDATION_FAILED")).toEqual({ kind: "error", message: "로그인 요청이 만료됐습니다. 다시 시도해 주세요." });
  });

  it("모르는 code는 일반 안내로 바꾼다", () => {
    expect(decideLoginNotice("SOMETHING_NEW")).toEqual({ kind: "error", message: "로그인하지 못했습니다. 다시 시도해 주세요." });
  });
});
