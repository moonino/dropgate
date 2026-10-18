import { NextResponse } from "next/server";

export const FOUND = 302;

export function redirectFound(path: string, request: Request): NextResponse {
  return NextResponse.redirect(new URL(path, request.url), FOUND);
}
