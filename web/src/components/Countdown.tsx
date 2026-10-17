"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { splitRemainingTime } from "@/domain/time";
import { useNow } from "./use-now";

const TICK_MILLIS = 1000;
const FINISHED_MESSAGE = "시각이 됐습니다. 상태를 확인합니다";
const MILESTONES = [
  { seconds: 600, message: "10분 남았습니다" },
  { seconds: 60, message: "1분 남았습니다" },
  { seconds: 10, message: "10초 남았습니다" },
];

type CountdownProps = { label: string; targetIso: string; absoluteLabel: string; renderedAtMillis: number };

export function Countdown({ label, targetIso, absoluteLabel, renderedAtMillis }: CountdownProps) {
  const router = useRouter();
  const now = useNow(renderedAtMillis);
  const [initialSecondsLeft] = useState(() => countSecondsLeft(targetIso, renderedAtMillis));
  const refreshed = useRef(false);
  const remaining = splitRemainingTime(targetIso, now);
  const announcement = decideAnnouncement(initialSecondsLeft, countSecondsLeft(targetIso, now), remaining.finished);

  useEffect(() => {
    if (remaining.finished && !refreshed.current) {
      refreshed.current = true;
      router.refresh();
    }
  }, [remaining.finished, router]);

  const cells = [
    { value: remaining.hours, unit: "시간" },
    { value: remaining.minutes, unit: "분" },
    { value: remaining.seconds, unit: "초" },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
      <p className="muted" style={{ margin: 0, fontSize: 14 }}>
        {label}
      </p>
      <div role="timer" aria-label={`${label} ${remaining.hours}시간 ${remaining.minutes}분 ${remaining.seconds}초`} style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
        {cells.map((cell) => (
          <div key={cell.unit} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
            <span
              className="mono fade-number"
              aria-hidden="true"
              style={{ minWidth: "1.6em", padding: "12px 8px", background: "var(--color-bg)", border: "1px solid var(--color-line)", borderRadius: 12, textAlign: "center", fontSize: "clamp(48px, 9vw, 84px)", lineHeight: 1, fontWeight: 500 }}
            >
              {cell.value}
            </span>
            <span className="muted" aria-hidden="true" style={{ fontSize: 12 }}>
              {cell.unit}
            </span>
          </div>
        ))}
      </div>
      <p style={{ margin: 0 }}>{absoluteLabel}</p>
      <p role="status" className="visually-hidden">
        {announcement}
      </p>
    </div>
  );
}

function countSecondsLeft(targetIso: string, nowMillis: number): number {
  return Math.ceil((Date.parse(targetIso) - nowMillis) / TICK_MILLIS);
}

function decideAnnouncement(initialSecondsLeft: number, secondsLeft: number, finished: boolean): string {
  if (finished) return FINISHED_MESSAGE;
  const crossed = MILESTONES.filter((milestone) => milestone.seconds < initialSecondsLeft && secondsLeft <= milestone.seconds);
  return crossed.at(-1)?.message ?? "";
}
