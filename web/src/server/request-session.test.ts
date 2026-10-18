import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { BackendError } from "./backend-error";
import { expireSessionIfUnauthenticated, requireViewer } from "./request-session";

const { cookieStore, redirect } = vi.hoisted(() => ({
  cookieStore: { get: vi.fn(), set: vi.fn() },
  redirect: vi.fn((path: string) => {
    throw new Error(`redirect ${path}`);
  }),
}));

vi.mock("next/headers", () => ({ cookies: async () => cookieStore }));
vi.mock("next/navigation", () => ({ redirect }));

const AUTH = "http://auth.test";
const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => {
  cookieStore.get.mockReturnValue({ value: "expired-token" });
});

describe("requireViewer", () => {
  it("인증 서버가 401이면 만료된 세션 정리 경로로 보낸다", async () => {
    server.use(http.get(`${AUTH}/auth/me`, () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "x" }, { status: 401 })));

    await expect(requireViewer()).rejects.toThrow("redirect /session/expired");
  });

  it("액세스 토큰 쿠키가 없으면 로그인 화면으로 보낸다", async () => {
    cookieStore.get.mockReturnValue(undefined);

    await expect(requireViewer()).rejects.toThrow("redirect /login");
  });
});

describe("expireSessionIfUnauthenticated", () => {
  it("401 오류면 만료된 세션 정리 경로로 보낸다", () => {
    const unauthenticated = new BackendError(401, "UNAUTHENTICATED", "x", [], undefined);

    expect(() => expireSessionIfUnauthenticated(unauthenticated)).toThrow("redirect /session/expired");
  });

  it("401이 아닌 오류는 그대로 다시 던진다", () => {
    const forbidden = new BackendError(403, "FORBIDDEN", "x", [], undefined);

    expect(() => expireSessionIfUnauthenticated(forbidden)).toThrow(forbidden);
  });
});
