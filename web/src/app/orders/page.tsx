import type { Metadata } from "next";
import { redirect } from "next/navigation";
import type { OrderPage } from "@/api/types";
import { OrderList } from "@/components/OrderList";
import { callBackend } from "@/server/backend";
import { readRequestViewer } from "@/server/request-session";
import { cancelOrder, loadOrdersPage } from "./actions";

export const metadata: Metadata = { title: "내 주문" };
export const dynamic = "force-dynamic";

export default async function OrdersPage() {
  const viewer = await readRequestViewer();
  if (viewer.kind === "anonymous") redirect("/login");
  const firstPage = await callBackend<OrderPage>("order", "/api/orders/me", { accessToken: viewer.accessToken });
  return (
    <main className="page">
      <div className="page-title">
        <p className="page-title__eyebrow">MY BOARDING PASSES</p>
        <h1 className="page-title__heading">내 주문</h1>
      </div>
      <OrderList firstPage={firstPage} cancel={cancelOrder} loadPage={loadOrdersPage} />
    </main>
  );
}
