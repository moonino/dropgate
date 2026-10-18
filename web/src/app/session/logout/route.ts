import { NextResponse, type NextRequest } from "next/server";
import { clearSession } from "@/server/session";

export async function POST(request: NextRequest): Promise<Response> {
  const response = NextResponse.redirect(new URL("/drops", request.url), 303);
  clearSession(response.cookies);
  return response;
}
