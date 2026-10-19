import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { purchaseDrop } from "./actions";

const { cookieStore } = vi.hoisted(() => ({ cookieStore: { get: vi.fn(), set: vi.fn() } }));
vi.mock("next/headers", () => ({ cookies: async () => cookieStore }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => cookieStore.get.mockReturnValue({ value: "expired-token" }));

describe("purchaseDrop", () => {
  it("액세스 쿠키가 없으면 구매하지 않고 재발급 결과를 돌려준다", async () => {
    cookieStore.get.mockReturnValue(undefined);
    expect(await purchaseDrop("drop-1")).toEqual({ kind: "refresh-required" });
  });

  it("구매 서버가 401이면 재발급 결과를 돌려준다", async () => {
    server.use(http.post("http://order.test/api/drops/drop-1/orders", () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "x" }, { status: 401 })));
    expect(await purchaseDrop("drop-1")).toEqual({ kind: "refresh-required" });
  });

  it("구매 서버가 401이어도 세션 쿠키를 지우지 않는다", async () => {
    cookieStore.set.mockClear();
    server.use(http.post("http://order.test/api/drops/drop-1/orders", () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "x" }, { status: 401 })));
    await purchaseDrop("drop-1");
    expect(cookieStore.set).not.toHaveBeenCalled();
  });
});
