import type { NextRequest } from "next/server";
import type { TokenPair } from "@/api/types";
import { sendBackendRequest } from "@/server/backend";
import { BackendError } from "@/server/backend-error";
import { forwardSetCookies } from "@/server/forward-cookies";
import { createLoginErrorPath, redirectToAuthUnavailable } from "@/server/login-redirect";
import { redirectFound } from "@/server/redirect";
import { writeSession } from "@/server/session";

export async function GET(request: NextRequest): Promise<Response> {
  let upstream: Response;
  try {
    upstream = await sendBackendRequest("auth", `/auth/callback/kakao${request.nextUrl.search}`, {
      cookieHeader: request.headers.get("cookie") ?? "",
    });
  } catch (error) {
    return redirectToAuthUnavailable(error, request);
  }
  if (!upstream.ok) {
    const error = await BackendError.fromResponse(upstream);
    const failed = redirectFound(createLoginErrorPath(error.code, error.retryAfterSeconds), request);
    forwardSetCookies(upstream, failed);
    return failed;
  }
  const tokens = (await upstream.json()) as TokenPair;
  const response = redirectFound("/drops", request);
  writeSession(response.cookies, tokens);
  forwardSetCookies(upstream, response);
  return response;
}
