"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import type { Order } from "@/api/types";
import type { PurchaseOutcome } from "@/domain/purchase";
import { callBackend } from "@/server/backend";
import { BackendError } from "@/server/backend-error";
import { clearSession, readSession } from "@/server/session";

export async function purchaseDrop(dropId: string): Promise<PurchaseOutcome> {
  const cookieStore = await cookies();
  const session = readSession(cookieStore);
  if (session.kind === "anonymous") redirect("/login");
  try {
    const order = await callBackend<Order>("order", `/api/drops/${encodeURIComponent(dropId)}/orders`, {
      method: "POST",
      accessToken: session.accessToken,
    });
    revalidatePath(`/drops/${dropId}`);
    return { kind: "confirmed", orderId: order.id, confirmedAt: order.confirmedAt };
  } catch (error) {
    if (!(error instanceof BackendError)) throw error;
    if (error.status === 401) {
      clearSession(cookieStore);
      redirect("/login");
    }
    return { kind: "rejected", code: error.code, message: error.message, retryAfterSeconds: error.retryAfterSeconds };
  }
}
