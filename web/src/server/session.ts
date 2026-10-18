import type { TokenPair } from "@/api/types";

export const ACCESS_COOKIE = "dropgate_access";
export const REFRESH_COOKIE = "dropgate_refresh";
const REFRESH_COOKIE_PATH = "/session";
const REFRESH_MAX_AGE_SECONDS = 14 * 24 * 60 * 60;

export type Session = { kind: "anonymous" } | { kind: "authenticated"; accessToken: string };

export type CookieReader = {
  get(name: string): { value: string } | undefined;
};

export type CookieOptions = {
  httpOnly: boolean;
  secure: boolean;
  sameSite: "lax";
  path: string;
  maxAge: number;
};

export type CookieWriter = {
  set(name: string, value: string, options: CookieOptions): unknown;
};

export function readSession(cookies: CookieReader): Session {
  const accessToken = cookies.get(ACCESS_COOKIE)?.value;
  return accessToken ? { kind: "authenticated", accessToken } : { kind: "anonymous" };
}

export function writeSession(cookies: CookieWriter, tokens: TokenPair): void {
  cookies.set(ACCESS_COOKIE, tokens.accessToken, createOptions("/", tokens.expiresIn));
  cookies.set(REFRESH_COOKIE, tokens.refreshToken, createOptions(REFRESH_COOKIE_PATH, REFRESH_MAX_AGE_SECONDS));
}

export function clearSession(cookies: CookieWriter): void {
  cookies.set(ACCESS_COOKIE, "", createOptions("/", 0));
  cookies.set(REFRESH_COOKIE, "", createOptions(REFRESH_COOKIE_PATH, 0));
}

function createOptions(path: string, maxAge: number): CookieOptions {
  return { httpOnly: true, secure: true, sameSite: "lax", path, maxAge };
}
