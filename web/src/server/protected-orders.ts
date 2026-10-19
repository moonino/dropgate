import type { OrderPage } from "@/api/types";
import { describeAuthUnavailable } from "@/domain/login-errors";
import type { SessionRecoveryContext } from "@/domain/session-recovery";
import { callBackendOrUnavailable, type BackendResult } from "./backend";
import { recoverSessionIfUnauthenticated, requireViewer } from "./request-session";

export async function loadProtectedOrders(context: SessionRecoveryContext): Promise<BackendResult<OrderPage>> {
  const viewer = await requireViewer(context);
  if (viewer.kind === "unavailable") return { kind: "unavailable", notice: describeAuthUnavailable(viewer.retryAfterSeconds) };
  return callBackendOrUnavailable<OrderPage>("order", "/api/orders/me", { accessToken: viewer.accessToken })
    .catch((error: unknown) => recoverSessionIfUnauthenticated(error, context));
}
