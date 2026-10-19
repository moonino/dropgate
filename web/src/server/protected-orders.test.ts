import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { loadProtectedOrders } from "./protected-orders";

vi.mock("next/headers", () => ({ cookies: async () => ({ get: () => ({ value: "token" }) }) }));
vi.mock("next/navigation", () => ({ redirect: (path: string) => { throw new Error(`redirect ${path}`); } }));

const AUTH = "http://auth.test";
const ORDER = "http://order.test";
const PAGE = { items: [], nextCursor: null };
const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => server.use(
  http.get(`${AUTH}/auth/me`, () => HttpResponse.json({ id: "user", nickname: "사용자", role: "USER" })),
  http.get(`${ORDER}/api/orders/me`, () => HttpResponse.json(PAGE)),
));

describe("보호된 주문 조회", () => {
  it.each([["", "/session/refresh?next=%2Forders"], ["refreshed=1", "/session/expired"]])("인증 확인 뒤 주문 데이터의 401도 같은 재발급 표시를 쓴다: %s", async (search, destination) => {
    server.use(http.get(`${ORDER}/api/orders/me`, () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "거절" }, { status: 401 })));
    await expect(loadProtectedOrders({ path: "/orders", search: new URLSearchParams(search) })).rejects.toThrow(`redirect ${destination}`);
  });

  it("인증과 실제 주문 조회가 성공한 결과를 돌려준다", async () => {
    expect(await loadProtectedOrders({ path: "/orders", search: new URLSearchParams("refreshed=1") })).toEqual({ kind: "ok", body: PAGE });
  });

  it.each([`${AUTH}/auth/me`, `${ORDER}/api/orders/me`])("보호 데이터 로드 중 장애는 완료 대신 재시도 안내를 돌려준다: %s", async (endpoint) => {
    server.use(http.get(endpoint, () => HttpResponse.json({ code: "AUTH_UNAVAILABLE", message: "장애" }, { status: 503 })));
    expect(await loadProtectedOrders({ path: "/orders", search: new URLSearchParams("refreshed=1") })).toMatchObject({ kind: "unavailable" });
  });
});
