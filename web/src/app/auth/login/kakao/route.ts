import { NextResponse, type NextRequest } from "next/server";
import { sendBackendRequest } from "@/server/backend";
import { isMockMode } from "@/server/config";
import { forwardSetCookies } from "@/server/forward-cookies";
import { FOUND, redirectFound } from "@/server/redirect";

export async function GET(request: NextRequest): Promise<Response> {
  if (isMockMode()) return redirectFound("/login", request);
  const upstream = await sendBackendRequest("auth", "/auth/login/kakao", {});
  const location = upstream.headers.get("Location");
  if (upstream.status !== FOUND || !location) {
    return redirectFound("/login?error=KAKAO_UNAVAILABLE", request);
  }
  const response = new NextResponse(null, { status: FOUND, headers: { Location: location } });
  forwardSetCookies(upstream, response);
  return response;
}
