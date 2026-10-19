import { NextResponse, type NextRequest } from "next/server";
import { isMockMode } from "@/server/config";
import { MOCK_USERS } from "@/server/mock/mock-store";
import { readMockSessions } from "@/server/mock/mock-session-store";
import { writeSession } from "@/server/session";

export async function POST(request: NextRequest): Promise<Response> {
  if (!isMockMode()) return new NextResponse(null, { status: 404 });
  const form = await request.formData();
  const userKey = `mock-${String(form.get("role"))}`;
  const user = MOCK_USERS[userKey];
  if (!user) return new NextResponse(null, { status: 400 });
  const response = NextResponse.redirect(new URL("/drops", request.url), 303);
  writeSession(response.cookies, readMockSessions().issueTokenPair(user));
  return response;
}
