"use client";

import Link from "next/link";
import { useState, useTransition } from "react";
import type { Drop, DropPage } from "@/api/types";
import { DROP_STATUS_BADGES, formatStock } from "@/domain/status";
import { formatKstDay, formatKstTime } from "@/domain/time";
import { StatusBadge } from "./StatusBadge";

type DropBoardProps = {
  firstPage: DropPage;
  loadPage: (cursor: string) => Promise<DropPage>;
};

export function DropBoard({ firstPage, loadPage }: DropBoardProps) {
  const [drops, setDrops] = useState<Drop[]>(firstPage.items);
  const [nextCursor, setNextCursor] = useState<string | null>(firstPage.nextCursor);
  const [pending, startTransition] = useTransition();

  const loadMore = (cursor: string) =>
    startTransition(async () => {
      const page = await loadPage(cursor);
      setDrops((current) => [...current, ...page.items]);
      setNextCursor(page.nextCursor);
    });

  if (drops.length === 0) return <p className="notice">지금 보드에 올라온 드롭이 없습니다.</p>;

  return (
    <>
      <ol aria-label="드롭 보드" style={{ margin: 0, padding: 0, listStyle: "none", borderTop: "1px solid var(--color-border)" }}>
        {drops.map((drop) => (
          <DropRow key={drop.id} drop={drop} />
        ))}
      </ol>
      {nextCursor !== null && (
        <div style={{ display: "flex", justifyContent: "center" }}>
          <button type="button" className="button" disabled={pending} onClick={() => loadMore(nextCursor)}>
            {pending ? "불러오는 중" : "더 보기"}
          </button>
        </div>
      )}
    </>
  );
}

function DropRow({ drop }: { drop: Drop }) {
  return (
    <li style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 16, padding: "20px 8px", borderBottom: "1px solid var(--color-line)" }}>
      <span style={{ flex: "0 0 150px", display: "flex", flexDirection: "column" }}>
        <span className="mono" style={{ fontSize: 22, fontWeight: 500 }}>
          {formatKstTime(drop.openAt)}
        </span>
        <span className="muted" style={{ fontSize: 13 }}>
          {formatKstDay(drop.openAt)}
        </span>
      </span>
      <span style={{ flex: "999 1 240px", minWidth: 0, fontSize: 19, fontWeight: 700, lineHeight: 1.35 }}>{drop.name}</span>
      <span className="mono" style={{ flex: "0 0 110px", fontSize: 15 }}>
        <span className="visually-hidden">남은 수량 </span>
        {formatStock(drop)}
      </span>
      <span style={{ flex: "0 0 120px" }}>
        <StatusBadge badge={DROP_STATUS_BADGES[drop.status]} />
      </span>
      <Link href={`/drops/${drop.id}`} className="button button--small" aria-label={`${drop.name} 보기`}>
        보기
      </Link>
    </li>
  );
}
