import type { Metadata } from "next";
import type { DropPage } from "@/api/types";
import { DropBoard } from "@/components/DropBoard";
import { UnavailableNotice } from "@/components/UnavailableNotice";
import { callBackendOrUnavailable } from "@/server/backend";
import { loadDropsPage } from "./actions";

export const metadata: Metadata = { title: "드롭" };
export const dynamic = "force-dynamic";

export default async function DropsPage() {
  const firstPage = await callBackendOrUnavailable<DropPage>("order", "/api/drops");
  return (
    <main className="page">
      <div style={{ display: "flex", flexWrap: "wrap", alignItems: "flex-end", justifyContent: "space-between", gap: 20 }}>
        <div className="page-title">
          <p className="page-title__eyebrow">DEPARTURES</p>
          <h1 className="page-title__heading">드롭</h1>
        </div>
        <p className="muted" style={{ margin: 0, maxWidth: 380, fontSize: 15 }}>
          오픈 시각 순서입니다. 판매가 끝난 드롭은 보드에서 내려갑니다.
        </p>
      </div>
      {firstPage.kind === "ok" ? <DropBoard firstPage={firstPage.body} loadPage={loadDropsPage} /> : <UnavailableNotice message={firstPage.notice} />}
    </main>
  );
}
