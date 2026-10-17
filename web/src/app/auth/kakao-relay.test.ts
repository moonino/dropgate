import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { NextRequest } from "next/server";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { GET as handleCallback } from "./callback/kakao/route";
import { GET as startLogin } from "./login/kakao/route";

const AUTH = "http://auth.test";
const WEB = "http://localhost:3000";
const STATE_COOKIE = "dropgate_oauth_state=signed; Path=/auth; Max-Age=600; HttpOnly; Secure; SameSite=Lax";
const CLEARED_STATE_COOKIE = "dropgate_oauth_state=; Path=/auth; Max-Age=0; HttpOnly; Secure; SameSite=Lax";

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => {
  vi.stubEnv("DROPGATE_WEB_AUTH_BASE_URL", AUTH);
});

describe("카카오 로그인 시작 중계", () => {
  it("auth-service의 카카오 리다이렉트와 state 쿠키를 그대로 돌려준다", async () => {
    server.use(
      http.get(`${AUTH}/auth/login/kakao`, () =>
        new HttpResponse(null, { status: 302, headers: { Location: "https://kauth.kakao.com/oauth/authorize?state=s", "Set-Cookie": STATE_COOKIE } }),
      ),
    );

    const response = await startLogin(new NextRequest(`${WEB}/auth/login/kakao`));

    expect(response.status).toBe(302);
    expect(response.headers.get("Location")).toBe("https://kauth.kakao.com/oauth/authorize?state=s");
    expect(response.headers.getSetCookie()).toContain(STATE_COOKIE);
  });

  it("auth-service가 리다이렉트하지 못하면 로그인 화면에 카카오 오류를 띄운다", async () => {
    server.use(http.get(`${AUTH}/auth/login/kakao`, () => HttpResponse.json({ code: "AUTH_UNAVAILABLE", message: "x" }, { status: 503 })));

    const response = await startLogin(new NextRequest(`${WEB}/auth/login/kakao`));

    expect(response.headers.get("Location")).toBe(`${WEB}/login?error=KAKAO_UNAVAILABLE`);
  });
});

describe("카카오 콜백 중계", () => {
  it("code, state, state 쿠키를 auth-service에 그대로 넘긴다", async () => {
    let forwarded = { search: "", cookie: "" };
    server.use(
      http.get(`${AUTH}/auth/callback/kakao`, ({ request }) => {
        forwarded = { search: new URL(request.url).search, cookie: request.headers.get("Cookie") ?? "" };
        return HttpResponse.json({ accessToken: "a", refreshToken: "r", expiresIn: 900, tokenType: "Bearer" });
      }),
    );

    await handleCallback(new NextRequest(`${WEB}/auth/callback/kakao?code=c&state=s`, { headers: { cookie: "dropgate_oauth_state=signed" } }));

    expect(forwarded).toEqual({ search: "?code=c&state=s", cookie: "dropgate_oauth_state=signed" });
  });

  it("토큰 쌍을 httpOnly 쿠키에 담고 드롭 목록으로 302 보낸다", async () => {
    server.use(
      http.get(`${AUTH}/auth/callback/kakao`, () =>
        HttpResponse.json({ accessToken: "access-token", refreshToken: "refresh-token", expiresIn: 900, tokenType: "Bearer" }),
      ),
    );

    const response = await handleCallback(new NextRequest(`${WEB}/auth/callback/kakao?code=c&state=s`));
    const cookies = response.headers.getSetCookie();

    expect(response.status).toBe(302);
    expect(response.headers.get("Location")).toBe(`${WEB}/drops`);
    expect(cookies.find((cookie) => cookie.startsWith("dropgate_access=access-token"))).toMatch(/HttpOnly/i);
    expect(cookies.find((cookie) => cookie.startsWith("dropgate_refresh=refresh-token"))).toMatch(/Path=\/session/);
  });

  it("세션 쿠키를 쓴 뒤에도 auth-service의 state 쿠키 삭제 헤더를 그대로 돌려준다", async () => {
    server.use(
      http.get(`${AUTH}/auth/callback/kakao`, () =>
        HttpResponse.json(
          { accessToken: "access-token", refreshToken: "refresh-token", expiresIn: 900, tokenType: "Bearer" },
          { headers: { "Set-Cookie": CLEARED_STATE_COOKIE } },
        ),
      ),
    );

    const response = await handleCallback(new NextRequest(`${WEB}/auth/callback/kakao?code=c&state=s`));

    expect(response.headers.getSetCookie()).toContain(CLEARED_STATE_COOKIE);
  });

  it("auth-service가 503이면 Retry-After 초를 들고 로그인 화면으로 보낸다", async () => {
    server.use(
      http.get(`${AUTH}/auth/callback/kakao`, () =>
        HttpResponse.json({ code: "AUTH_UNAVAILABLE", message: "x", errors: [] }, { status: 503, headers: { "Retry-After": "2" } }),
      ),
    );

    const response = await handleCallback(new NextRequest(`${WEB}/auth/callback/kakao?code=c&state=s`));

    expect(response.headers.get("Location")).toBe(`${WEB}/login?error=AUTH_UNAVAILABLE&retryAfter=2`);
  });

  it("auth-service가 거절하면 오류 code를 들고 로그인 화면으로 보낸다", async () => {
    server.use(
      http.get(`${AUTH}/auth/callback/kakao`, () =>
        HttpResponse.json({ code: "VALIDATION_FAILED", message: "로그인 state가 유효하지 않습니다", errors: [] }, { status: 400 }),
      ),
    );

    const response = await handleCallback(new NextRequest(`${WEB}/auth/callback/kakao?code=c&state=forged`));

    expect(response.headers.get("Location")).toBe(`${WEB}/login?error=VALIDATION_FAILED`);
    expect(response.headers.getSetCookie().some((cookie) => cookie.startsWith("dropgate_access="))).toBe(false);
  });
});
