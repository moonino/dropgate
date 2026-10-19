import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { createSessionRecoveryPath, type SessionRecoveryContext } from "@/domain/session-recovery";
import { BackendError } from "./backend-error";
import { clearSession, readSession, type Session } from "./session";
import { fetchViewer, isSignedOut, type ActiveViewer, type Viewer } from "./viewer";

export async function readRequestSession(): Promise<Session> {
  return readSession(await cookies());
}

export async function readRequestViewer(): Promise<Viewer> {
  return fetchViewer(await readRequestSession());
}

export async function requireViewer(context: SessionRecoveryContext): Promise<ActiveViewer> {
  const viewer = await readRequestViewer();
  if (isSignedOut(viewer)) redirect(createSessionRecoveryPath(context));
  return viewer;
}

export async function requireAccessToken(): Promise<string> {
  const session = await readRequestSession();
  if (session.kind === "anonymous") redirect("/login");
  return session.accessToken;
}

export async function signOutAndRedirect(): Promise<never> {
  clearSession(await cookies());
  redirect("/login");
}

export async function signOutIfUnauthenticated(error: unknown): Promise<never> {
  if (error instanceof BackendError && error.isUnauthenticated()) await signOutAndRedirect();
  throw error;
}

export function recoverSessionIfUnauthenticated(error: unknown, context: SessionRecoveryContext): never {
  if (error instanceof BackendError && error.isUnauthenticated()) redirect(createSessionRecoveryPath(context));
  throw error;
}
