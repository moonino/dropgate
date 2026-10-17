import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { clearSession, readSession, type Session } from "./session";
import { fetchViewer, type Viewer } from "./viewer";

export async function readRequestSession(): Promise<Session> {
  return readSession(await cookies());
}

export async function readRequestViewer(): Promise<Viewer> {
  return fetchViewer(await readRequestSession());
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
