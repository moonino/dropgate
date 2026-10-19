"use server";

import { revalidatePath } from "next/cache";
import type { Order, OrderPage } from "@/api/types";
import type { CancelOutcome } from "@/domain/cancel";
import type { SessionActionResult } from "@/domain/session-action";
import { runAuthenticatedAction } from "@/server/authenticated-action";
import { callBackend, callBackendOrUnavailable, type BackendResult } from "@/server/backend";

export async function cancelOrder(orderId: string): Promise<SessionActionResult<CancelOutcome>> {
  return runAuthenticatedAction<CancelOutcome>(async (accessToken) => {
    const order = await callBackend<Order>("order", `/api/orders/${encodeURIComponent(orderId)}/cancel`, { method: "POST", accessToken });
    revalidatePath(`/drops/${order.dropId}`);
    return { kind: "canceled", order };
  }, (error) => {
    return { kind: "rejected", code: error.code, message: error.message, retryAfterSeconds: error.retryAfterSeconds };
  });
}

export async function loadOrdersPage(cursor: string): Promise<SessionActionResult<BackendResult<OrderPage>>> {
  return runAuthenticatedAction((accessToken) => callBackendOrUnavailable<OrderPage>("order", `/api/orders/me?cursor=${encodeURIComponent(cursor)}`, { accessToken }));
}
