"use server";

import { revalidatePath } from "next/cache";
import type { Order } from "@/api/types";
import type { PurchaseOutcome } from "@/domain/purchase";
import type { SessionActionResult } from "@/domain/session-action";
import { runAuthenticatedAction } from "@/server/authenticated-action";
import { callBackend } from "@/server/backend";

export async function purchaseDrop(dropId: string): Promise<SessionActionResult<PurchaseOutcome>> {
  return runAuthenticatedAction<PurchaseOutcome>(async (accessToken) => {
    const order = await callBackend<Order>("order", `/api/drops/${encodeURIComponent(dropId)}/orders`, {
      method: "POST",
      accessToken,
    });
    revalidatePath(`/drops/${dropId}`);
    return { kind: "confirmed", orderId: order.id, confirmedAt: order.confirmedAt };
  }, (error) => {
    return { kind: "rejected", code: error.code, message: error.message, retryAfterSeconds: error.retryAfterSeconds };
  });
}
