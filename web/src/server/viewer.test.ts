import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { fetchViewer } from "./viewer";

const AUTH = "http://auth.test";
const SIGNED_IN = { kind: "authenticated", accessToken: "token" } as const;
const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("fetchViewer", () => {
  it("인증 서버가 503이면 비로그인 대신 응답 없음과 Retry-After 초를 돌려준다", async () => {
    server.use(http.get(`${AUTH}/auth/me`, () => HttpResponse.json({ code: "AUTH_UNAVAILABLE", message: "x" }, { status: 503, headers: { "Retry-After": "2" } })));

    expect(await fetchViewer(SIGNED_IN)).toEqual({ kind: "unavailable", retryAfterSeconds: 2 });
  });

  it("인증 서버에 연결하지 못해도 응답 없음으로 돌려준다", async () => {
    server.use(http.get(`${AUTH}/auth/me`, () => HttpResponse.error()));

    expect(await fetchViewer(SIGNED_IN)).toEqual({ kind: "unavailable", retryAfterSeconds: undefined });
  });

  it("인증 서버가 401이면 비로그인으로 본다", async () => {
    server.use(http.get(`${AUTH}/auth/me`, () => HttpResponse.json({ code: "UNAUTHENTICATED", message: "x" }, { status: 401 })));

    expect(await fetchViewer(SIGNED_IN)).toEqual({ kind: "anonymous" });
  });
});
