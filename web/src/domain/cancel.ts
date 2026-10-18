import type { Order } from "@/api/types";

export type CancelOutcome = { kind: "canceled"; order: Order } | { kind: "rejected"; code: string; message: string; retryAfterSeconds: number | undefined };

export type CancelRejection = Extract<CancelOutcome, { kind: "rejected" }>;

export type CancelControl = { kind: "button" } | { kind: "disabled"; message: string } | { kind: "note"; text: string };

const DISABLING_REJECTIONS = new Set(["ORDER_ALREADY_CANCELED", "DROP_CLOSED"]);

const FINAL_REJECTIONS = new Set(["FORBIDDEN", "ORDER_NOT_FOUND"]);

export function decideCancelControl(order: Pick<Order, "status">, lastRejection: CancelRejection | undefined): CancelControl {
  if (order.status === "CANCELED") return { kind: "note", text: "다시 살 수 없습니다" };
  if (lastRejection && DISABLING_REJECTIONS.has(lastRejection.code)) return { kind: "disabled", message: lastRejection.message };
  if (lastRejection && FINAL_REJECTIONS.has(lastRejection.code)) return { kind: "note", text: lastRejection.message };
  return { kind: "button" };
}
