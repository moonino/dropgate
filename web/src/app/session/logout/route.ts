import { NextResponse, type NextRequest } from "next/server";
import { sendBackendRequest } from "@/server/backend";
import { clearSession, readSession } from "@/server/session";

export async function POST(request: NextRequest): Promise<Response> {
  const session = readSession(request.cookies);
  if (session.kind === "authenticated") await notifyBackendLogout(session.accessToken);
  const response = NextResponse.redirect(new URL("/drops", request.url), 303);
  clearSession(response.cookies);
  return response;
}

async function notifyBackendLogout(accessToken: string): Promise<void> {
  try {
    const response = await sendBackendRequest("auth", "/auth/logout", { method: "POST", accessToken });
    if (!response.ok) console.error("백엔드 로그아웃에 실패했습니다", response.status);
  } catch (error) {
    console.error("백엔드 로그아웃에 실패했습니다", error);
  }
}
