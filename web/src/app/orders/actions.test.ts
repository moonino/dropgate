import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { cancelOrder, loadOrdersPage } from "./actions";

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
  server.use(http.post(`${ORDER}/api/orders/order-1/cancel`, () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "x" }, { status: 401 })));
});

describe.each([
  { name: "주문 취소", perform: () => cancelOrder("order-1") },
  { name: "주문 더 보기", perform: () => loadOrdersPage("cursor-1") },
])("$name", ({ perform }) => {
  it("액세스 쿠키가 없으면 재발급 결과를 돌려준다", async () => {
    cookieStore.get.mockReturnValue(undefined);
    expect(await perform()).toEqual({ kind: "refresh-required" });
  });

  it("백엔드가 401이면 재발급 결과를 돌려준다", async () => {
    expect(await perform()).toEqual({ kind: "refresh-required" });
  });
});
