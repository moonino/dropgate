import type { NextResponse } from "next/server";
import { BackendError } from "./backend-error";
import { redirectFound } from "./redirect";

const AUTH_UNAVAILABLE = "AUTH_UNAVAILABLE";

export function createLoginErrorPath(code: string, retryAfterSeconds: number | undefined): string {
  const query = new URLSearchParams({ error: code });
  if (retryAfterSeconds !== undefined) query.set("retryAfter", String(retryAfterSeconds));
  return `/login?${query}`;
}

export function redirectToAuthUnavailable(error: unknown, request: Request): NextResponse {
  if (!(error instanceof BackendError)) throw error;
  return redirectFound(createLoginErrorPath(AUTH_UNAVAILABLE, error.retryAfterSeconds), request);
}
