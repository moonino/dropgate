"use server";

import { revalidatePath } from "next/cache";
import type { Order } from "@/api/types";
import type { PurchaseOutcome } from "@/domain/purchase";
import { callBackend } from "@/server/backend";
import { BackendError } from "@/server/backend-error";
import { requireAccessToken, signOutAndRedirect } from "@/server/request-session";

export async function purchaseDrop(dropId: string): Promise<PurchaseOutcome> {
  const accessToken = await requireAccessToken();
  try {
    const order = await callBackend<Order>("order", `/api/drops/${encodeURIComponent(dropId)}/orders`, {
      method: "POST",
      accessToken,
    });
    revalidatePath(`/drops/${dropId}`);
    return { kind: "confirmed", orderId: order.id, confirmedAt: order.confirmedAt };
  } catch (error) {
    if (!(error instanceof BackendError)) throw error;
    if (error.isUnauthenticated()) await signOutAndRedirect();
    return { kind: "rejected", code: error.code, message: error.message, retryAfterSeconds: error.retryAfterSeconds };
  }
}
