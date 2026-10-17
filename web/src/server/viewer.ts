import type { User } from "@/api/types";
import { callBackend } from "./backend";
import { BackendError } from "./backend-error";
import type { Session } from "./session";

export type Viewer = { kind: "anonymous" } | { kind: "signed-in"; user: User; accessToken: string };

const ANONYMOUS: Viewer = { kind: "anonymous" };

export async function fetchViewer(session: Session): Promise<Viewer> {
  if (session.kind === "anonymous") return ANONYMOUS;
  try {
    const user = await callBackend<User>("auth", "/auth/me", { accessToken: session.accessToken });
    return { kind: "signed-in", user, accessToken: session.accessToken };
  } catch (error) {
    if (error instanceof BackendError && error.status === 401) return ANONYMOUS;
    throw error;
  }
}

export function isAdmin(viewer: Viewer): boolean {
  return viewer.kind === "signed-in" && viewer.user.role === "ADMIN";
}
