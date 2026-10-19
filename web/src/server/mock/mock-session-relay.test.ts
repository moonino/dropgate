import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { POST as login } from "@/app/auth/mock/route";
import type { TokenPair, User } from "@/api/types";
import { callBackend, sendBackendRequest } from "../backend";

beforeEach(() => vi.stubEnv("DROPGATE_WEB_MOCK", "true"));
afterEach(() => vi.unstubAllEnvs());

async function loginUser(): Promise<{ accessToken: string; refreshToken: string }> {
  const request = new NextRequest("http://localhost:3000/auth/mock", { method: "POST", body: new URLSearchParams({ role: "USER" }) });
  const response = await login(request);
  const readCookie = (name: string) => {
    const cookie = response.headers.getSetCookie().find((value) => value.startsWith(`${name}=`));
    if (!cookie) throw new Error(`모의 로그인 쿠키가 없습니다: ${name}`);
    return cookie.split(";")[0]!.slice(name.length + 1);
  };
  return { accessToken: readCookie("dropgate_access"), refreshToken: readCookie("dropgate_refresh") };
}

describe("모의 세션 중계", () => {
  it("로그인과 인증 확인이 같은 저장소를 쓴다", async () => {
    const tokens = await loginUser();
    expect(await callBackend<User>("auth", "/auth/me", { accessToken: tokens.accessToken })).toMatchObject({ role: "USER" });
  });

  it("재발급한 액세스 토큰을 인증 확인에 쓴다", async () => {
    const tokens = await loginUser();
    const rotated = await callBackend<TokenPair>("auth", "/auth/token/refresh", { method: "POST", body: { refreshToken: tokens.refreshToken } });
    expect(await callBackend<User>("auth", "/auth/me", { accessToken: rotated.accessToken })).toMatchObject({ role: "USER" });
  });

  it("모의 백엔드는 사용한 리프레시에 401을 돌려준다", async () => {
    const tokens = await loginUser();
    const request = { method: "POST", body: { refreshToken: tokens.refreshToken } } as const;
    await callBackend<TokenPair>("auth", "/auth/token/refresh", request);
    await expect(callBackend("auth", "/auth/token/refresh", request)).rejects.toMatchObject({ status: 401 });
  });

  it.each([{}, { refreshToken: 17 }, null])("리프레시 필드가 없거나 문자열이 아니면 400이다: %j", async (body) => {
    await expect(callBackend("auth", "/auth/token/refresh", { method: "POST", body })).rejects.toMatchObject({ status: 400 });
  });

  it("로그아웃은 본문 없는 204를 돌려준다", async () => {
    const tokens = await loginUser();
    const response = await sendBackendRequest("auth", "/auth/logout", { method: "POST", accessToken: tokens.accessToken });
    expect(response.status).toBe(204);
  });

  it("로그아웃한 토큰은 인증 확인에서 401이다", async () => {
    const tokens = await loginUser();
    await sendBackendRequest("auth", "/auth/logout", { method: "POST", accessToken: tokens.accessToken });
    await expect(callBackend("auth", "/auth/me", { accessToken: tokens.accessToken })).rejects.toMatchObject({ status: 401 });
  });

  it("production에서는 모의 로그인을 열지 않는다", async () => {
    vi.stubEnv("NODE_ENV", "production");
    expect((await login(new NextRequest("http://localhost:3000/auth/mock", { method: "POST" }))).status).toBe(404);
  });
});
