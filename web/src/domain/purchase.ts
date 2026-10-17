import type { Drop } from "@/api/types";

export type PurchaseOutcome =
  | { kind: "confirmed"; orderId: string; confirmedAt: string }
  | { kind: "rejected"; code: string; message: string; retryAfterSeconds: number | undefined };

export type BuyButton = { kind: "enabled"; label: string } | { kind: "disabled"; label: string };

const REJECTION_LABELS: Record<string, string> = {
  SOLD_OUT: "품절",
  ALREADY_PURCHASED: "이미 구매함",
  DROP_NOT_OPEN: "판매 중이 아님",
  NOT_ADMITTED: "입장 필요",
};

const RETRYABLE_LABEL = "다시 시도";

export function decideBuyButton(drop: Pick<Drop, "status">, opensAtLabel: string, outcome: PurchaseOutcome | undefined): BuyButton {
  if (outcome?.kind === "confirmed") return { kind: "disabled", label: "구매 완료" };
  if (outcome?.kind === "rejected") {
    const label = REJECTION_LABELS[outcome.code];
    return label ? { kind: "disabled", label } : { kind: "enabled", label: RETRYABLE_LABEL };
  }
  if (drop.status === "OPEN") return { kind: "enabled", label: "구매하기" };
  if (drop.status === "SCHEDULED") return { kind: "disabled", label: `${opensAtLabel} 오픈` };
  if (drop.status === "SOLD_OUT") return { kind: "disabled", label: "품절" };
  return { kind: "disabled", label: "판매 종료" };
}

export type CountdownTarget = { kind: "counting"; label: string; targetIso: string; milestone: string } | { kind: "ended" };

export function decideCountdownTarget(drop: Pick<Drop, "status" | "openAt" | "closeAt">): CountdownTarget {
  if (drop.status === "SCHEDULED") return { kind: "counting", label: "오픈까지", targetIso: drop.openAt, milestone: "오픈" };
  if (drop.status === "CLOSED") return { kind: "ended" };
  return { kind: "counting", label: "마감까지", targetIso: drop.closeAt, milestone: "마감" };
}
