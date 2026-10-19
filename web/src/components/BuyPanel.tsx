"use client";

import Link from "next/link";
import { useState, useTransition } from "react";
import type { Drop } from "@/api/types";
import { decideBuyButton, type PurchaseOutcome } from "@/domain/purchase";
import { appendRetryAfter } from "@/domain/retry";
import type { SessionActionResult } from "@/domain/session-action";
import { formatKstStamp, formatKstTime } from "@/domain/time";
import { navigateToSessionRecovery } from "./session-navigation";

type BuyPanelProps = {
  drop: Pick<Drop, "id" | "status" | "openAt">;
  signedIn: boolean;
  purchase: (dropId: string) => Promise<SessionActionResult<PurchaseOutcome>>;
};

export function BuyPanel({ drop, signedIn, purchase }: BuyPanelProps) {
  const [outcome, setOutcome] = useState<PurchaseOutcome | undefined>(undefined);
  const [pending, startTransition] = useTransition();
  const button = decideBuyButton(drop, formatKstTime(drop.openAt), outcome);

  if (!signedIn) {
    return (
      <Link href="/login" className="button button--large">
        로그인하고 구매하기
      </Link>
    );
  }

  const buy = () => startTransition(async () => {
    const result = await purchase(drop.id);
    if (result.kind === "refresh-required") return navigateToSessionRecovery();
    setOutcome(result);
  });

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <button
        type="button"
        className={button.kind === "enabled" ? "button button--large button--primary" : "button button--large"}
        disabled={button.kind === "disabled" || pending}
        onClick={buy}
      >
        {pending ? "확인 중" : button.label}
      </button>
      {outcome?.kind === "confirmed" && (
        <div role="status" className="notice" style={{ borderColor: "var(--color-accent)", display: "flex", flexDirection: "column", gap: 6 }}>
          <strong>구매가 확정됐습니다</strong>
          <span className="mono muted" style={{ fontSize: 13 }}>
            주문 {outcome.orderId.slice(-8).toUpperCase()} / {formatKstStamp(outcome.confirmedAt)}
          </span>
          <Link href="/orders">내 주문에서 보기</Link>
        </div>
      )}
      {outcome?.kind === "rejected" && <p role="alert" style={{ margin: 0, fontSize: 14 }}>{appendRetryAfter(outcome.message, outcome.retryAfterSeconds)}</p>}
    </div>
  );
}
