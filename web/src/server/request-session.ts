import { cookies } from "next/headers";
import { readSession, type Session } from "./session";
import { fetchViewer, type Viewer } from "./viewer";

export async function readRequestSession(): Promise<Session> {
  return readSession(await cookies());
}

export async function readRequestViewer(): Promise<Viewer> {
  return fetchViewer(await readRequestSession());
}
