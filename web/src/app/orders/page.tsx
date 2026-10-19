import type { Metadata } from "next";
import { ConsumeRefreshMarker } from "@/components/ConsumeRefreshMarker";
import { OrderList } from "@/components/OrderList";
import { UnavailableNotice } from "@/components/UnavailableNotice";
import { createPageRecoveryContext, type PageSearchParams } from "@/domain/session-recovery";
import { loadProtectedOrders } from "@/server/protected-orders";
import { cancelOrder, loadOrdersPage } from "./actions";

export const metadata: Metadata = { title: "내 주문" };
export const dynamic = "force-dynamic";

export default async function OrdersPage({ searchParams }: { searchParams: Promise<PageSearchParams> }) {
  const firstPage = await loadProtectedOrders(createPageRecoveryContext("/orders", await searchParams));
  return (
    <main className="page">
      <div className="page-title">
        <p className="page-title__eyebrow">MY BOARDING PASSES</p>
        <h1 className="page-title__heading">내 주문</h1>
      </div>
      {firstPage.kind === "unavailable" ? <UnavailableNotice message={firstPage.notice} /> : (
        <>
          <ConsumeRefreshMarker />
          <OrderList firstPage={firstPage.body} cancel={cancelOrder} loadPage={loadOrdersPage} />
        </>
      )}
    </main>
  );
}
