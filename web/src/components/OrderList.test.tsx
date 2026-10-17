import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { createOrder } from "@/test/fixtures";
import { OrderList } from "./OrderList";

describe("OrderList", () => {
  it("취소하면 그 주문을 취소됨으로 바꾸고 결과를 알린다", async () => {
    const order = createOrder();
    const cancel = vi.fn().mockResolvedValue({ kind: "canceled", order: { ...order, status: "CANCELED", canceledAt: "2026-10-17T10:40:00.000Z" } });
    render(<OrderList firstPage={{ items: [order], nextCursor: null }} cancel={cancel} loadPage={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: `${order.dropName} 주문 취소` }));

    expect(cancel).toHaveBeenCalledWith(order.id);
    expect(await screen.findByText("취소됨")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("주문을 취소했습니다");
  });

  it("마감 뒤라 거절되면 버튼을 서버 문구로 바꾼다", async () => {
    const order = createOrder();
    const cancel = vi.fn().mockResolvedValue({ kind: "rejected", code: "DROP_CLOSED", message: "마감한 드롭의 주문은 취소할 수 없습니다", retryAfterSeconds: undefined });
    render(<OrderList firstPage={{ items: [order], nextCursor: null }} cancel={cancel} loadPage={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: `${order.dropName} 주문 취소` }));

    expect(await screen.findAllByText("마감한 드롭의 주문은 취소할 수 없습니다")).not.toHaveLength(0);
    expect(screen.queryByRole("button", { name: /주문 취소/ })).not.toBeInTheDocument();
  });

  it("503이면 알림에 Retry-After 초 뒤 다시 시도하라는 안내를 붙인다", async () => {
    const order = createOrder();
    const cancel = vi.fn().mockResolvedValue({ kind: "rejected", code: "STOCK_UNAVAILABLE", message: "재고 서버가 바쁩니다.", retryAfterSeconds: 2 });
    render(<OrderList firstPage={{ items: [order], nextCursor: null }} cancel={cancel} loadPage={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: `${order.dropName} 주문 취소` }));

    expect(await screen.findByText("재고 서버가 바쁩니다. 2초 뒤 다시 시도해 주세요.")).toHaveAttribute("role", "status");
  });

  it("더 보기가 503이면 목록을 두고 다시 시도 안내를 보여 준다", async () => {
    const loadPage = vi.fn().mockResolvedValue({ kind: "unavailable", notice: "잠시 바쁩니다. 2초 뒤 다시 시도해 주세요." });
    render(<OrderList firstPage={{ items: [createOrder()], nextCursor: "cursor-1" }} cancel={vi.fn()} loadPage={loadPage} />);

    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    expect(await screen.findByText("잠시 바쁩니다. 2초 뒤 다시 시도해 주세요.")).toBeInTheDocument();
  });

  it("주문이 없으면 빈 안내를 보여 준다", () => {
    render(<OrderList firstPage={{ items: [], nextCursor: null }} cancel={vi.fn()} loadPage={vi.fn()} />);

    expect(screen.getByText("아직 주문이 없습니다.")).toBeInTheDocument();
  });
});
