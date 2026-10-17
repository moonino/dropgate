import { NextResponse, type NextRequest } from "next/server";
import { sendBackendRequest } from "@/server/backend";
import { isMockMode } from "@/server/config";
import { forwardSetCookies } from "@/server/forward-cookies";

const FOUND = 302;

export async function GET(request: NextRequest): Promise<Response> {
  if (isMockMode()) return NextResponse.redirect(new URL("/login", request.url));
  const upstream = await sendBackendRequest("auth", "/auth/login/kakao", {});
  const location = upstream.headers.get("Location");
  if (upstream.status !== FOUND || !location) {
    return NextResponse.redirect(new URL("/login?error=KAKAO_UNAVAILABLE", request.url));
  }
  const response = new NextResponse(null, { status: FOUND, headers: { Location: location } });
  forwardSetCookies(upstream, response);
  return response;
}
