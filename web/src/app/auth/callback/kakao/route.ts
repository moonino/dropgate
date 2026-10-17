import type { NextRequest } from "next/server";
import type { TokenPair } from "@/api/types";
import { sendBackendRequest } from "@/server/backend";
import { BackendError } from "@/server/backend-error";
import { forwardSetCookies } from "@/server/forward-cookies";
import { redirectFound } from "@/server/redirect";
import { writeSession } from "@/server/session";

export async function GET(request: NextRequest): Promise<Response> {
  const upstream = await sendBackendRequest("auth", `/auth/callback/kakao${request.nextUrl.search}`, {
    cookieHeader: request.headers.get("cookie") ?? "",
  });
  if (!upstream.ok) {
    const failed = redirectFound(createLoginErrorPath(await BackendError.fromResponse(upstream)), request);
    forwardSetCookies(upstream, failed);
    return failed;
  }
  const tokens = (await upstream.json()) as TokenPair;
  const response = redirectFound("/drops", request);
  writeSession(response.cookies, tokens);
  forwardSetCookies(upstream, response);
  return response;
}

function createLoginErrorPath(error: BackendError): string {
  const query = new URLSearchParams({ error: error.code });
  if (error.retryAfterSeconds !== undefined) query.set("retryAfter", String(error.retryAfterSeconds));
  return `/login?${query}`;
}
