import { NextResponse, type NextRequest } from "next/server";
import { isMockMode } from "@/server/config";
import { MOCK_USERS } from "@/server/mock/mock-store";
import { writeSession } from "@/server/session";

const MOCK_TOKEN_SECONDS = 900;

export async function POST(request: NextRequest): Promise<Response> {
  if (!isMockMode()) return new NextResponse(null, { status: 404 });
  const form = await request.formData();
  const token = `mock-${String(form.get("role"))}`;
  if (!MOCK_USERS[token]) return new NextResponse(null, { status: 400 });
  const response = NextResponse.redirect(new URL("/drops", request.url), 303);
  writeSession(response.cookies, { accessToken: token, refreshToken: token, expiresIn: MOCK_TOKEN_SECONDS, tokenType: "Bearer" });
  return response;
}
