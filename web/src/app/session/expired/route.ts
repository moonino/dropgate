import type { NextRequest } from "next/server";
import { redirectFound } from "@/server/redirect";
import { clearSession } from "@/server/session";

export async function GET(request: NextRequest): Promise<Response> {
  const response = redirectFound("/login", request);
  clearSession(response.cookies);
  return response;
}
