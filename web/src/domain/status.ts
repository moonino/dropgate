import type { Drop, DropStatus, OrderStatus } from "@/api/types";

export type BadgeTone = "outline" | "accent" | "muted" | "dashed";

export type Badge = { label: string; tone: BadgeTone };

export const DROP_STATUS_BADGES: Record<DropStatus, Badge> = {
  SCHEDULED: { label: "오픈 예정", tone: "outline" },
  OPEN: { label: "판매 중", tone: "accent" },
  SOLD_OUT: { label: "품절", tone: "muted" },
  CLOSED: { label: "마감", tone: "dashed" },
};

export const ORDER_STATUS_BADGES: Record<OrderStatus, Badge> = {
  CONFIRMED: { label: "확정", tone: "outline" },
  CANCELED: { label: "취소됨", tone: "dashed" },
};

export const STOCK_NOT_COUNTED = "집계 전";

export function formatStock(drop: Pick<Drop, "remainingQuantity" | "totalQuantity">): string {
  if (drop.remainingQuantity === null || drop.remainingQuantity === undefined) return STOCK_NOT_COUNTED;
  return `${drop.remainingQuantity} / ${drop.totalQuantity}`;
}
