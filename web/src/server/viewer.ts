import type { User } from "@/api/types";
import { callBackend } from "./backend";
import { BackendError } from "./backend-error";
import type { Session } from "./session";

export type Viewer =
  | { kind: "anonymous" }
  | { kind: "expired" }
  | { kind: "signed-in"; user: User; accessToken: string }
  | { kind: "unavailable"; retryAfterSeconds: number | undefined };

export type SignedOutViewer = Extract<Viewer, { kind: "anonymous" | "expired" }>;

export type ActiveViewer = Exclude<Viewer, SignedOutViewer>;

const ANONYMOUS: Viewer = { kind: "anonymous" };
const EXPIRED: Viewer = { kind: "expired" };

export async function fetchViewer(session: Session): Promise<Viewer> {
  if (session.kind === "anonymous") return ANONYMOUS;
  try {
    const user = await callBackend<User>("auth", "/auth/me", { accessToken: session.accessToken });
    return { kind: "signed-in", user, accessToken: session.accessToken };
  } catch (error) {
    if (!(error instanceof BackendError)) throw error;
    if (error.isUnauthenticated()) return EXPIRED;
    if (error.isUnavailable()) return { kind: "unavailable", retryAfterSeconds: error.retryAfterSeconds };
    throw error;
  }
}

export function isSignedOut(viewer: Viewer): viewer is SignedOutViewer {
  return viewer.kind === "anonymous" || viewer.kind === "expired";
}

export function isAdmin(viewer: Viewer): boolean {
  return viewer.kind === "signed-in" && viewer.user.role === "ADMIN";
}
