import type { Metadata } from "next";
import { notFound } from "next/navigation";
import type { Drop } from "@/api/types";
import { BuyPanel } from "@/components/BuyPanel";
import { Countdown } from "@/components/Countdown";
import { StatusBadge } from "@/components/StatusBadge";
import { decideCountdownTarget } from "@/domain/purchase";
import { DROP_STATUS_BADGES, formatStock } from "@/domain/status";
import { formatKstDay, formatKstStamp, formatKstTime } from "@/domain/time";
import { callBackend } from "@/server/backend";
import { BackendError } from "@/server/backend-error";
import { readServerTimeMillis } from "@/server/clock";
import { readRequestViewer } from "@/server/request-session";
import { purchaseDrop } from "./actions";

export const metadata: Metadata = { title: "드롭 상세" };
export const dynamic = "force-dynamic";

type DropPageProps = { params: Promise<{ dropId: string }> };

export default async function DropDetailPage({ params }: DropPageProps) {
  const { dropId } = await params;
  const [drop, viewer] = await Promise.all([fetchDrop(dropId), readRequestViewer()]);
  const target = decideCountdownTarget(drop);
  return (
    <main className="page" style={{ paddingTop: 32 }}>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <StatusBadge badge={DROP_STATUS_BADGES[drop.status]} />
        <h1 className="page-title__heading" style={{ fontSize: "clamp(34px, 6vw, 52px)" }}>
          {drop.name}
        </h1>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 16 }}>
        <section className="panel" aria-label="카운트다운과 구매" style={{ gridColumn: "1 / -1", display: "flex", flexWrap: "wrap", alignItems: "flex-end", justifyContent: "space-between", gap: 28 }}>
          {target.kind === "counting" ? (
            <Countdown label={target.label} targetIso={target.targetIso} absoluteLabel={`${formatKstDay(target.targetIso)} ${formatKstTime(target.targetIso)} ${target.milestone}`}
              renderedAtMillis={readServerTimeMillis()}
            />
          ) : (
            <p style={{ margin: 0, fontSize: 20 }}>{formatKstDay(drop.closeAt)} {formatKstTime(drop.closeAt)} 마감했습니다</p>
          )}
          <div style={{ flex: "0 1 340px", minWidth: 260 }}>
            <BuyPanel drop={drop} signedIn={viewer.kind === "signed-in"} purchase={purchaseDrop} />
          </div>
        </section>
        <section className="panel" aria-label="남은 수량">
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>남은 수량</p>
          <p className="mono" style={{ margin: "12px 0 0", fontSize: 40, fontWeight: 500 }}>{formatStock(drop)}</p>
        </section>
        <section className="panel" aria-label="일정">
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>일정</p>
          <dl style={{ margin: "12px 0 0", display: "grid", gap: 10 }}>
            <ScheduleRow label="오픈" value={formatKstStamp(drop.openAt)} />
            <ScheduleRow label="마감" value={formatKstStamp(drop.closeAt)} />
            <ScheduleRow label="초당 입장" value={`${drop.admissionRatePerSec}명`} />
          </dl>
        </section>
        <section className="panel" aria-label="규칙">
          <p className="muted" style={{ margin: 0, fontSize: 14 }}>규칙</p>
          <ul style={{ margin: "12px 0 0", paddingLeft: 18, display: "grid", gap: 8 }}>
            <li>1인 1개까지 살 수 있습니다.</li>
            <li>마감 전까지 취소할 수 있고, 취소한 드롭은 다시 살 수 없습니다.</li>
            <li>결제는 없습니다. 구매하면 바로 확정됩니다.</li>
          </ul>
        </section>
      </div>
    </main>
  );
}

function ScheduleRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
      <dt className="muted">{label}</dt>
      <dd className="mono" style={{ margin: 0 }}>{value}</dd>
    </div>
  );
}

async function fetchDrop(dropId: string): Promise<Drop> {
  try {
    return await callBackend<Drop>("order", `/api/drops/${encodeURIComponent(dropId)}`);
  } catch (error) {
    if (error instanceof BackendError && (error.status === 404 || error.status === 400)) notFound();
    throw error;
  }
}
