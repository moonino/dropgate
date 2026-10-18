import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { createDropAction } from "./actions";

const { cookieStore, redirect } = vi.hoisted(() => ({
  cookieStore: { get: vi.fn(), set: vi.fn() },
  redirect: vi.fn((path: string) => {
    throw new Error(`redirect ${path}`);
  }),
}));

vi.mock("next/headers", () => ({ cookies: async () => cookieStore }));
vi.mock("next/navigation", () => ({ redirect }));

const ORDER = "http://order.test";
const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => {
  cookieStore.get.mockReturnValue({ value: "expired-token" });
  cookieStore.set.mockClear();
  redirect.mockClear();
  server.use(http.post(`${ORDER}/api/admin/drops`, () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "x" }, { status: 401 })));
});

function createValidForm(): FormData {
  const form = new FormData();
  form.set("name", "[테스트] 스니커즈");
  form.set("totalQuantity", "100");
  form.set("admissionRatePerSec", "50");
  form.set("openAt", "2026-10-18T19:30");
  form.set("closeAt", "2026-10-19T19:30");
  return form;
}

describe("createDropAction", () => {
  it("백엔드가 401이면 두 세션 쿠키를 지운다", async () => {
    await createDropAction({ kind: "idle" }, createValidForm()).catch(() => undefined);

    expect(cookieStore.set).toHaveBeenCalledWith("dropgate_access", "", expect.objectContaining({ maxAge: 0 }));
    expect(cookieStore.set).toHaveBeenCalledWith("dropgate_refresh", "", expect.objectContaining({ maxAge: 0 }));
  });

  it("백엔드가 401이면 로그인 화면으로 보낸다", async () => {
    await expect(createDropAction({ kind: "idle" }, createValidForm())).rejects.toThrow("redirect /login");
  });
});
