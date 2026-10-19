import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { BackendError } from "./backend-error";
import { readRequestViewer, recoverSessionIfUnauthenticated, requireViewer } from "./request-session";

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
  redirect.mockClear();
});

const INITIAL = { path: "/orders", search: new URLSearchParams() };
const REFRESHED = { path: "/orders", search: new URLSearchParams("refreshed=1") };
const REFRESH_PATH = "/session/refresh?next=%2Forders";

describe("requireViewer", () => {
  it("인증 서버가 401이면 재발급 경로로 보낸다", async () => {
    server.use(http.get(`${AUTH}/auth/me`, () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "x" }, { status: 401 })));

    await expect(requireViewer(INITIAL)).rejects.toThrow(`redirect ${REFRESH_PATH}`);
  });

  it.each([{ context: INITIAL, destination: REFRESH_PATH }, { context: REFRESHED, destination: "/session/expired" }])("액세스 쿠키가 없으면 표시로 이동을 구분한다: $destination", async ({ context, destination }) => {
    cookieStore.get.mockReturnValue(undefined);

    await expect(requireViewer(context)).rejects.toThrow(`redirect ${destination}`);
  });

  it("재발급 직후 인증 서버가 401이면 만료 정리로 보낸다", async () => {
    server.use(http.get(`${AUTH}/auth/me`, () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "x" }, { status: 401 })));
    await expect(requireViewer(REFRESHED)).rejects.toThrow("redirect /session/expired");
  });

  it.each([undefined, { value: "expired-token" }])("공개 화면의 사용자 조회는 재발급하지 않는다: %j", async (cookie) => {
    cookieStore.get.mockReturnValue(cookie);
    server.use(http.get(`${AUTH}/auth/me`, () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "x" }, { status: 401 })));
    await readRequestViewer();
    expect(redirect).not.toHaveBeenCalled();
  });
});

describe("recoverSessionIfUnauthenticated", () => {
  it("401 오류면 재발급 경로로 보낸다", () => {
    const unauthenticated = new BackendError(401, "UNAUTHENTICATED", "x", [], undefined);

    expect(() => recoverSessionIfUnauthenticated(unauthenticated, INITIAL)).toThrow(`redirect ${REFRESH_PATH}`);
  });

  it("401이 아닌 오류는 그대로 다시 던진다", () => {
    const forbidden = new BackendError(403, "FORBIDDEN", "x", [], undefined);

    expect(() => recoverSessionIfUnauthenticated(forbidden, INITIAL)).toThrow(forbidden);
  });
});
