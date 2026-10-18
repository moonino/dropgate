import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { GET as clearExpiredSession } from "./route";

const WEB = "http://localhost:3000";

describe("만료된 세션 정리", () => {
  it("두 세션 쿠키를 지운다", async () => {
    const cookies = (await clearExpiredSession(new NextRequest(`${WEB}/session/expired`))).headers.getSetCookie();

    expect(cookies.find((cookie) => cookie.startsWith("dropgate_access="))).toMatch(/Max-Age=0/);
    expect(cookies.find((cookie) => cookie.startsWith("dropgate_refresh="))).toMatch(/Max-Age=0/);
  });

  it("로그인 화면으로 302 보낸다", async () => {
    const response = await clearExpiredSession(new NextRequest(`${WEB}/session/expired`));

    expect(response.status).toBe(302);
    expect(response.headers.get("Location")).toBe(`${WEB}/login`);
  });
});
