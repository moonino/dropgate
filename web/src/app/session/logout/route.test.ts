import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { NextRequest } from "next/server";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const AUTH = "http://auth.test";
const WEB = "http://web.test";
const LOGOUT_RESPONSES = [
  { name: "본문 없는 204", reply: () => new HttpResponse(null, { status: 204 }) },
  { name: "503", reply: () => HttpResponse.json({ code: "AUTH_UNAVAILABLE", message: "x" }, { status: 503 }) },
  { name: "연결 실패", reply: () => HttpResponse.error() },
];
const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => { server.resetHandlers(); vi.restoreAllMocks(); });
afterAll(() => server.close());
beforeEach(() => vi.spyOn(console, "error").mockImplementation(() => undefined));

function createLogoutRequest(cookie = "dropgate_access=access-token"): NextRequest {
  return new NextRequest(`${WEB}/session/logout`, { method: "POST", headers: { Cookie: cookie } });
}

describe("POST /session/logout", () => {
  it("액세스 쿠키를 Bearer로 백엔드에 보낸다", async () => {
    const received = vi.fn();
    server.use(http.post(`${AUTH}/auth/logout`, ({ request }) => {
      received(request.headers.get("Authorization"));
      return new HttpResponse(null, { status: 204 });
    }));
    await POST(createLogoutRequest());
    expect(received).toHaveBeenCalledWith("Bearer access-token");
  });

  it.each(LOGOUT_RESPONSES)("$name여도 두 쿠키를 지운다", async ({ reply }) => {
    server.use(http.post(`${AUTH}/auth/logout`, reply));
    const response = await POST(createLogoutRequest());
    expect(response.headers.getSetCookie()).toEqual([
      expect.stringMatching(/dropgate_access=; Path=\/;.*Max-Age=0/),
      expect.stringMatching(/dropgate_refresh=; Path=\/session;.*Max-Age=0/),
    ]);
  });

  it.each(LOGOUT_RESPONSES)("$name여도 드롭 목록으로 303 이동한다", async ({ reply }) => {
    server.use(http.post(`${AUTH}/auth/logout`, reply));
    const response = await POST(createLogoutRequest());
    expect(response.status).toBe(303);
    expect(response.headers.get("Location")).toBe(`${WEB}/drops`);
  });

  it("본문 없는 204 성공은 실패로 기록하지 않는다", async () => {
    server.use(http.post(`${AUTH}/auth/logout`, () => new HttpResponse(null, { status: 204 })));
    await POST(createLogoutRequest());
    expect(console.error).not.toHaveBeenCalled();
  });

  it("액세스 쿠키가 없으면 백엔드를 호출하지 않는다", async () => {
    const logout = vi.fn(() => new HttpResponse(null, { status: 204 }));
    server.use(http.post(`${AUTH}/auth/logout`, logout));
    await POST(createLogoutRequest("dropgate_refresh=refresh-token"));
    expect(logout).not.toHaveBeenCalled();
  });

  it("실패한 백엔드 호출을 서버에만 기록한다", async () => {
    server.use(http.post(`${AUTH}/auth/logout`, () => new HttpResponse(null, { status: 503 })));
    await POST(createLogoutRequest());
    expect(console.error).toHaveBeenCalledWith("백엔드 로그아웃에 실패했습니다", 503);
  });
});
