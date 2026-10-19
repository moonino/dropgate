import { SESSION_REFRESH_REQUIRED, type SessionActionResult } from "@/domain/session-action";
import { BackendError } from "./backend-error";
import { readRequestSession } from "./request-session";

export async function runAuthenticatedAction<T>(perform: (accessToken: string) => Promise<T>, onRejected: (error: BackendError) => T = throwBackendError): Promise<SessionActionResult<T>> {
  const session = await readRequestSession();
  if (session.kind === "anonymous") return SESSION_REFRESH_REQUIRED;
  try {
    return await perform(session.accessToken);
  } catch (error) {
    if (!(error instanceof BackendError)) throw error;
    if (error.isUnauthenticated()) return SESSION_REFRESH_REQUIRED;
    return onRejected(error);
  }
}

function throwBackendError(error: BackendError): never {
  throw error;
}
