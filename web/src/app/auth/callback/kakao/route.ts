import { NextResponse, type NextRequest } from "next/server";
import type { TokenPair } from "@/api/types";
import { sendBackendRequest } from "@/server/backend";
import { BackendError } from "@/server/backend-error";
import { forwardSetCookies } from "@/server/forward-cookies";
import { writeSession } from "@/server/session";

export async function GET(request: NextRequest): Promise<Response> {
  const upstream = await sendBackendRequest("auth", `/auth/callback/kakao${request.nextUrl.search}`, {
    cookieHeader: request.headers.get("cookie") ?? "",
  });
  if (!upstream.ok) {
    const error = await BackendError.fromResponse(upstream);
    const failed = NextResponse.redirect(new URL(`/login?error=${encodeURIComponent(error.code)}`, request.url));
    forwardSetCookies(upstream, failed);
    return failed;
  }
  const tokens = (await upstream.json()) as TokenPair;
  const response = NextResponse.redirect(new URL("/drops", request.url));
  writeSession(response.cookies, tokens);
  forwardSetCookies(upstream, response);
  return response;
}
