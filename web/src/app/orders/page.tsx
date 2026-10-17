import type { Metadata } from "next";
import { redirect } from "next/navigation";
import type { OrderPage } from "@/api/types";
import { OrderList } from "@/components/OrderList";
import { UnavailableNotice } from "@/components/UnavailableNotice";
import { describeAuthUnavailable } from "@/domain/login-errors";
import { callBackendOrUnavailable } from "@/server/backend";
import { readRequestViewer } from "@/server/request-session";
import type { Viewer } from "@/server/viewer";
import { cancelOrder, loadOrdersPage } from "./actions";

export const metadata: Metadata = { title: "내 주문" };
export const dynamic = "force-dynamic";

export default async function OrdersPage() {
  const viewer = await readRequestViewer();
  if (viewer.kind === "anonymous") redirect("/login");
  return (
    <main className="page">
      <div className="page-title">
        <p className="page-title__eyebrow">MY BOARDING PASSES</p>
        <h1 className="page-title__heading">내 주문</h1>
      </div>
      <Orders viewer={viewer} />
    </main>
  );
}

async function Orders({ viewer }: { viewer: Exclude<Viewer, { kind: "anonymous" }> }) {
  if (viewer.kind === "unavailable") return <UnavailableNotice message={describeAuthUnavailable(viewer.retryAfterSeconds)} />;
  const firstPage = await callBackendOrUnavailable<OrderPage>("order", "/api/orders/me", { accessToken: viewer.accessToken });
  if (firstPage.kind === "unavailable") return <UnavailableNotice message={firstPage.notice} />;
  return <OrderList firstPage={firstPage.body} cancel={cancelOrder} loadPage={loadOrdersPage} />;
}
