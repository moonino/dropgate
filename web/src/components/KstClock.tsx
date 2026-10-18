"use client";

import { formatKstStamp } from "@/domain/time";
import { useNow } from "./use-now";

export function KstClock({ renderedAtMillis }: { renderedAtMillis: number }) {
  const now = useNow(renderedAtMillis);
  const time = formatKstStamp(new Date(now).toISOString()).split(" ")[1];
  return (
    <span className="mono muted" style={{ fontSize: 13 }} aria-label="한국 시각">
      KST {time}
    </span>
  );
}
