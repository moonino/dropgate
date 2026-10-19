import { NextResponse, type NextRequest } from "next/server";
import type { TokenPair } from "@/api/types";
import { callBackend } from "@/server/backend";
import { BackendError } from "@/server/backend-error";
import { createLoginErrorPath } from "@/server/login-redirect";
import { FOUND, redirectFound } from "@/server/redirect";
import { clearSession, REFRESH_COOKIE, writeSession } from "@/server/session";
import { DEFAULT_RETURN_PATH, REFRESHED_PARAMETER, resolveSessionReturnUrl } from "@/server/session-return";

const LOGIN_PATH = "/login";
const AUTH_UNAVAILABLE = "AUTH_UNAVAILABLE";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
  if (!refreshToken) return clearSessionAndRedirect(request);
  try {
    const tokens = await callBackend<TokenPair>("auth", "/auth/token/refresh", { method: "POST", body: { refreshToken } });
    const destination = resolveSessionReturnUrl(request.nextUrl.searchParams.get("next") ?? DEFAULT_RETURN_PATH, request.url);
    destination.searchParams.set(REFRESHED_PARAMETER, "1");
    const response = NextResponse.redirect(destination, FOUND);
    writeSession(response.cookies, tokens);
    return response;
  } catch (error) {
    if (!(error instanceof BackendError)) throw error;
    if (error.isUnauthenticated()) return clearSessionAndRedirect(request);
    if (error.isUnavailable()) return redirectFound(createLoginErrorPath(AUTH_UNAVAILABLE, error.retryAfterSeconds), request);
    throw error;
  }
}

function clearSessionAndRedirect(request: NextRequest): NextResponse {
  const response = redirectFound(LOGIN_PATH, request);
  clearSession(response.cookies);
  return response;
}
