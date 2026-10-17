import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { callBackend } from "./backend";
import { BackendError } from "./backend-error";
const ORDER = "http://order.test";
const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("callBackend", () => {
  it("세션의 액세스 토큰을 Bearer로 붙인다", async () => {
    let authorization = "";
    server.use(
      http.get(`${ORDER}/api/orders/me`, ({ request }) => {
        authorization = request.headers.get("Authorization") ?? "";
        return HttpResponse.json({ items: [], nextCursor: null });
      }),
    );

    await callBackend("order", "/api/orders/me", { accessToken: "token" });

    expect(authorization).toBe("Bearer token");
  });

  it("오류 응답의 code, message, 필드 오류, Retry-After를 예외에 담는다", async () => {
    server.use(
      http.post(`${ORDER}/api/drops/d/orders`, () =>
        HttpResponse.json({ code: "STOCK_UNAVAILABLE", message: "잠시 뒤 다시", errors: [{ field: "x", message: "y" }] }, { status: 503, headers: { "Retry-After": "2" } }),
      ),
    );

    const error = await callBackend("order", "/api/drops/d/orders", { method: "POST" }).catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(BackendError);
    expect(error).toMatchObject({ status: 503, code: "STOCK_UNAVAILABLE", message: "잠시 뒤 다시", fieldErrors: [{ field: "x", message: "y" }], retryAfterSeconds: 2 });
  });

  it("오류 본문이 계약 형식이 아니면 UNKNOWN으로 바꾼다", async () => {
    server.use(http.get(`${ORDER}/api/drops`, () => new HttpResponse("<html>bad gateway</html>", { status: 502 })));

    const error = await callBackend("order", "/api/drops").catch((caught: unknown) => caught);

    expect(error).toMatchObject({ status: 502, code: "UNKNOWN" });
  });
});

describe("모의 모드", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("네트워크 대신 모의 핸들러가 답한다", async () => {
    vi.stubEnv("DROPGATE_WEB_MOCK", "true");

    const page = await callBackend<{ items: unknown[] }>("order", "/api/drops");

    expect(page.items.length).toBeGreaterThan(0);
  });
});
