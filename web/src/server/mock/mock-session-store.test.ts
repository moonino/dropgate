import { describe, expect, it } from "vitest";
import { MockSessionStore } from "./mock-session-store";
import { MOCK_USERS } from "./mock-store";

const USER = MOCK_USERS["mock-USER"]!;
const ADMIN = MOCK_USERS["mock-ADMIN"]!;

function createStore(): MockSessionStore {
  let sequence = 0;
  return new MockSessionStore(() => String(++sequence));
}

describe("MockSessionStore", () => {
  it("발급한 액세스 토큰으로 사용자를 확인한다", () => {
    const store = createStore();
    const tokens = store.issueTokenPair(USER);
    expect(store.authenticate(tokens.accessToken)).toEqual({ kind: "ok", value: USER });
  });

  it("회전한 액세스 토큰으로 같은 사용자를 확인한다", () => {
    const store = createStore();
    const rotated = store.rotateTokenPair(store.issueTokenPair(USER).refreshToken);
    if (rotated.kind !== "ok") throw new Error("모의 재발급이 실패했습니다");
    expect(store.authenticate(rotated.value.accessToken)).toEqual({ kind: "ok", value: USER });
  });

  it("사용한 리프레시 토큰은 다시 쓸 수 없다", () => {
    const store = createStore();
    const tokens = store.issueTokenPair(USER);
    store.rotateTokenPair(tokens.refreshToken);
    expect(store.rotateTokenPair(tokens.refreshToken)).toMatchObject({ kind: "rejected", rejection: { status: 401 } });
  });

  it("로그아웃한 액세스 토큰을 거절한다", () => {
    const store = createStore();
    const tokens = store.issueTokenPair(USER);
    store.logout(tokens.accessToken);
    expect(store.authenticate(tokens.accessToken)).toMatchObject({ kind: "rejected", rejection: { status: 401 } });
  });

  it("로그아웃한 사용자의 리프레시 토큰을 모두 거절한다", () => {
    const store = createStore();
    const first = store.issueTokenPair(USER);
    const second = store.issueTokenPair(USER);
    store.logout(first.accessToken);
    for (const tokens of [first, second]) {
      expect(store.rotateTokenPair(tokens.refreshToken)).toMatchObject({ kind: "rejected", rejection: { status: 401 } });
    }
  });

  it("다른 사용자의 세션은 로그아웃하지 않는다", () => {
    const store = createStore();
    const user = store.issueTokenPair(USER);
    const admin = store.issueTokenPair(ADMIN);
    store.logout(user.accessToken);
    expect(store.rotateTokenPair(admin.refreshToken)).toMatchObject({ kind: "ok" });
  });
});
