"use client";

import { useState, useTransition } from "react";
import type { Order, OrderPage } from "@/api/types";
import { decideCancelControl, type CancelOutcome } from "@/domain/cancel";
import { ORDER_STATUS_BADGES } from "@/domain/status";
import { formatKstStamp } from "@/domain/time";
import { StatusBadge } from "./StatusBadge";

type OrderListProps = {
  firstPage: OrderPage;
  cancel: (orderId: string) => Promise<CancelOutcome>;
  loadPage: (cursor: string) => Promise<OrderPage>;
};

export function OrderList({ firstPage, cancel, loadPage }: OrderListProps) {
  const [orders, setOrders] = useState<Order[]>(firstPage.items);
  const [nextCursor, setNextCursor] = useState<string | null>(firstPage.nextCursor);
  const [rejections, setRejections] = useState<Record<string, CancelOutcome>>({});
  const [notice, setNotice] = useState("");
  const [pending, startTransition] = useTransition();

  const cancelOne = (orderId: string) =>
    startTransition(async () => {
      const outcome = await cancel(orderId);
      if (outcome.kind === "canceled") {
        setOrders((current) => current.map((order) => (order.id === orderId ? outcome.order : order)));
        setNotice("주문을 취소했습니다. 수량 1개가 드롭으로 돌아갔습니다.");
        return;
      }
      setRejections((current) => ({ ...current, [orderId]: outcome }));
      setNotice(outcome.message);
    });

  const loadMore = (cursor: string) =>
    startTransition(async () => {
      const page = await loadPage(cursor);
      setOrders((current) => [...current, ...page.items]);
      setNextCursor(page.nextCursor);
    });

  if (orders.length === 0) return <p className="notice">아직 주문이 없습니다.</p>;

  return (
    <>
      <p role="status" className={notice ? "notice" : "visually-hidden"}>
        {notice}
      </p>
      <ul style={{ margin: 0, padding: 0, listStyle: "none", display: "flex", flexDirection: "column", gap: 12 }}>
        {orders.map((order) => (
          <OrderRow key={order.id} order={order} rejection={rejections[order.id]} pending={pending} onCancel={cancelOne} />
        ))}
      </ul>
      {nextCursor !== null && (
        <div style={{ display: "flex", justifyContent: "center" }}>
          <button type="button" className="button" disabled={pending} onClick={() => loadMore(nextCursor)}>
            더 보기
          </button>
        </div>
      )}
    </>
  );
}

type OrderRowProps = { order: Order; rejection: CancelOutcome | undefined; pending: boolean; onCancel: (orderId: string) => void };

function OrderRow({ order, rejection, pending, onCancel }: OrderRowProps) {
  const control = decideCancelControl(order, rejection);
  const canceled = order.status === "CANCELED";
  return (
    <li className="panel" style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 20, padding: "22px 24px" }}>
      <div style={{ flex: "999 1 280px", minWidth: 0, display: "flex", flexDirection: "column", gap: 6 }}>
        <span style={{ fontSize: 18, fontWeight: 700, color: canceled ? "var(--color-text-muted)" : undefined }}>{order.dropName}</span>
        <span className="mono muted" style={{ fontSize: 13 }}>
          주문 {formatKstStamp(order.confirmedAt)}
          {order.canceledAt ? ` / 취소 ${formatKstStamp(order.canceledAt)}` : ""}
        </span>
      </div>
      <StatusBadge badge={ORDER_STATUS_BADGES[order.status]} />
      <div style={{ flex: "0 0 180px", display: "flex", justifyContent: "flex-end" }}>
        {control.kind === "button" ? (
          <button type="button" className="button button--small button--danger" disabled={pending} onClick={() => onCancel(order.id)} aria-label={`${order.dropName} 주문 취소`}>
            주문 취소
          </button>
        ) : (
          <span className="muted" style={{ fontSize: 13, textAlign: "right" }}>
            {control.text}
          </span>
        )}
      </div>
    </li>
  );
}
