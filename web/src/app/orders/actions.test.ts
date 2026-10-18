import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { loadOrdersPage } from "./actions";

const { cookieStore, redirect } = vi.hoisted(() => ({
  cookieStore: { get: vi.fn(), set: vi.fn() },
  redirect: vi.fn((path: string) => {
    throw new Error(`redirect ${path}`);
  }),
}));

vi.mock("next/headers", () => ({ cookies: async () => cookieStore }));
vi.mock("next/navigation", () => ({ redirect }));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

const ORDER = "http://order.test";
const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => {
  cookieStore.get.mockReturnValue({ value: "expired-token" });
  cookieStore.set.mockClear();
  server.use(http.get(`${ORDER}/api/orders/me`, () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "x" }, { status: 401 })));
});

describe("loadOrdersPage", () => {
  it("백엔드가 401이면 두 세션 쿠키를 지운다", async () => {
    await loadOrdersPage("cursor-1").catch(() => undefined);

    expect(cookieStore.set).toHaveBeenCalledWith("dropgate_access", "", expect.objectContaining({ maxAge: 0 }));
    expect(cookieStore.set).toHaveBeenCalledWith("dropgate_refresh", "", expect.objectContaining({ maxAge: 0 }));
  });

  it("백엔드가 401이면 로그인 화면으로 보낸다", async () => {
    await expect(loadOrdersPage("cursor-1")).rejects.toThrow("redirect /login");
  });
});
