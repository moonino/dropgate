import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { NextRequest } from "next/server";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { GET } from "./route";

const WEB = "http://localhost:3000";
const AUTH = "http://auth.test";
const TOKENS = { accessToken: "new-access", refreshToken: "new-refresh", expiresIn: 900, tokenType: "Bearer" };
const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => server.use(http.post(`${AUTH}/auth/token/refresh`, () => HttpResponse.json(TOKENS))));

function createRequest(next = "/orders", token = "refresh-token"): NextRequest {
  const request = new NextRequest(`${WEB}/session/refresh?${new URLSearchParams({ next })}`);
  if (token) request.cookies.set("dropgate_refresh", token);
  return request;
}

describe("재발급 중계", () => {
  it("리프레시 쿠키를 백엔드 본문으로 보낸다", async () => {
    let body: unknown;
    server.use(http.post(`${AUTH}/auth/token/refresh`, async ({ request }) => {
      body = await request.json();
      return HttpResponse.json(TOKENS);
    }));
    await GET(createRequest());
    expect(body).toEqual({ refreshToken: "refresh-token" });
  });

  it("성공하면 두 세션 쿠키를 새로 쓴다", async () => {
    const response = await GET(createRequest());
    const cookies = response.headers.getSetCookie();
    expect(cookies.find((value) => value.startsWith("dropgate_access=new-access"))).toMatch(/Path=\/;.*Max-Age=900/);
    expect(cookies.find((value) => value.startsWith("dropgate_refresh=new-refresh"))).toMatch(/Path=\/session/);
  });

  it("성공하면 재발급 표시를 붙여 next로 302 보낸다", async () => {
    const response = await GET(createRequest());
    expect(response.status).toBe(302);
    expect(response.headers.get("Location")).toBe(`${WEB}/orders?refreshed=1`);
  });

  it("정상 next의 query와 hash를 보존한다", async () => {
    const response = await GET(createRequest("/orders?cursor=first#item"));
    expect(response.headers.get("Location")).toBe(`${WEB}/orders?cursor=first&refreshed=1#item`);
  });

  it.each(["https://evil.com", "//evil.com", "/\\evil.com", "/\t/evil.com", "/\n/evil.com", "/\\["])("외부 출처나 파싱할 수 없는 next는 드롭 목록으로 보낸다: %j", async (next) => {
    const response = await GET(createRequest(next));
    expect(response.headers.get("Location")).toBe(`${WEB}/drops?refreshed=1`);
  });

  it("정규화된 pathname을 재조립하지 않아 web 출처를 유지한다", async () => {
    const response = await GET(createRequest("/.//evil.com"));
    expect(response.headers.get("Location")).toBe(`${WEB}//evil.com?refreshed=1`);
  });

  it.each(["", "used-refresh"])("쿠키가 없거나 401이면 두 쿠키를 지운다: %j", async (token) => {
    server.use(http.post(`${AUTH}/auth/token/refresh`, () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "거절" }, { status: 401 })));
    const response = await GET(createRequest("/orders", token));
    expect(response.headers.getSetCookie().filter((value) => /Max-Age=0/.test(value))).toHaveLength(2);
    expect(response.headers.get("Location")).toBe(`${WEB}/login`);
  });

  it("리프레시 쿠키가 없으면 백엔드를 부르지 않는다", async () => {
    let calls = 0;
    server.use(http.post(`${AUTH}/auth/token/refresh`, () => { calls += 1; return HttpResponse.json(TOKENS); }));
    await GET(createRequest("/orders", ""));
    expect(calls).toBe(0);
  });

  it.each(["503", "connection"])("인증 서버 장애면 쿠키를 유지하고 오류 화면으로 보낸다: %s", async (failure) => {
    server.use(http.post(`${AUTH}/auth/token/refresh`, () => failure === "connection" ? HttpResponse.error() : HttpResponse.json({ code: "AUTH_UNAVAILABLE", message: "장애" }, { status: 503 })));
    const response = await GET(createRequest());
    expect(response.headers.getSetCookie()).toEqual([]);
    expect(response.headers.get("Location")).toBe(`${WEB}/login?error=AUTH_UNAVAILABLE`);
  });
});
