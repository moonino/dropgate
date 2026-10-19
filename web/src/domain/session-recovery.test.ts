import { describe, expect, it } from "vitest";
import { createPageRecoveryContext, createSessionRecoveryPath } from "./session-recovery";

describe("세션 복귀 경로", () => {
  it("페이지 query의 반복 값과 일반 값을 복귀 경로에 보존한다", () => {
    const context = createPageRecoveryContext("/orders", { cursor: "next", filter: ["a", "b"], absent: undefined });
    expect(createSessionRecoveryPath(context)).toBe("/session/refresh?next=%2Forders%3Fcursor%3Dnext%26filter%3Da%26filter%3Db");
  });

  it("페이지의 재발급 표시는 만료 정리 경로가 된다", () => {
    expect(createSessionRecoveryPath(createPageRecoveryContext("/orders", { refreshed: "1" }))).toBe("/session/expired");
  });

  it("클라이언트 복귀 경로의 hash를 보존한다", () => {
    expect(createSessionRecoveryPath({ path: "/orders", search: new URLSearchParams(), hash: "#row" })).toBe("/session/refresh?next=%2Forders%23row");
  });
});
