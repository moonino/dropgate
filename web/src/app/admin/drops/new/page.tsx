import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { DropForm } from "@/components/DropForm";
import { readRequestViewer } from "@/server/request-session";
import { isAdmin } from "@/server/viewer";
import { createDropAction } from "./actions";

export const metadata: Metadata = { title: "드롭 등록" };
export const dynamic = "force-dynamic";

export default async function NewDropPage() {
  const viewer = await readRequestViewer();
  if (viewer.kind === "anonymous") redirect("/login");
  if (!isAdmin(viewer)) {
    return (
      <main className="page">
        <h1 className="page-title__heading">드롭 등록</h1>
        <p role="alert" className="notice">
          관리자만 드롭을 등록할 수 있습니다.
        </p>
      </main>
    );
  }
  return (
    <main className="page" style={{ flexDirection: "row", flexWrap: "wrap", alignItems: "flex-start", gap: 40 }}>
      <div className="page-title" style={{ flex: "1 1 300px", minWidth: 0 }}>
        <p className="page-title__eyebrow">NEW DEPARTURE</p>
        <h1 className="page-title__heading" style={{ fontSize: 48 }}>
          드롭 등록
        </h1>
        <p className="muted" style={{ margin: 0, fontSize: 15 }}>
          등록하면 오픈 예정으로 보드에 올라갑니다. 오픈 시각이 되면 판매 중으로, 마감 시각이 되면 마감으로 저절로 바뀝니다.
        </p>
      </div>
      <DropForm action={createDropAction} />
    </main>
  );
}
