"use server";

import { revalidatePath } from "next/cache";
import type { Order, OrderPage } from "@/api/types";
import type { CancelOutcome } from "@/domain/cancel";
import { callBackend, callBackendOrUnavailable, type BackendResult } from "@/server/backend";
import { BackendError } from "@/server/backend-error";
import { requireAccessToken, signOutAndRedirect, signOutIfUnauthenticated } from "@/server/request-session";

export async function cancelOrder(orderId: string): Promise<CancelOutcome> {
  const accessToken = await requireAccessToken();
  try {
    const order = await callBackend<Order>("order", `/api/orders/${encodeURIComponent(orderId)}/cancel`, { method: "POST", accessToken });
    revalidatePath(`/drops/${order.dropId}`);
    return { kind: "canceled", order };
  } catch (error) {
    if (!(error instanceof BackendError)) throw error;
    if (error.isUnauthenticated()) await signOutAndRedirect();
    return { kind: "rejected", code: error.code, message: error.message, retryAfterSeconds: error.retryAfterSeconds };
  }
}

export async function loadOrdersPage(cursor: string): Promise<BackendResult<OrderPage>> {
  const accessToken = await requireAccessToken();
  return callBackendOrUnavailable<OrderPage>("order", `/api/orders/me?cursor=${encodeURIComponent(cursor)}`, { accessToken }).catch(signOutIfUnauthenticated);
}
