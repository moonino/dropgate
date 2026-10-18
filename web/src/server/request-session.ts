import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { BackendError } from "./backend-error";
import { clearSession, readSession, type Session } from "./session";
import { fetchViewer, type ActiveViewer, type Viewer } from "./viewer";

export const SESSION_EXPIRED_PATH = "/session/expired";

export async function readRequestSession(): Promise<Session> {
  return readSession(await cookies());
}

export async function readRequestViewer(): Promise<Viewer> {
  return fetchViewer(await readRequestSession());
}

export async function requireViewer(): Promise<ActiveViewer> {
  const viewer = await readRequestViewer();
  if (viewer.kind === "anonymous") redirect("/login");
  if (viewer.kind === "expired") redirect(SESSION_EXPIRED_PATH);
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

export function expireSessionIfUnauthenticated(error: unknown): never {
  if (error instanceof BackendError && error.isUnauthenticated()) redirect(SESSION_EXPIRED_PATH);
  throw error;
}
