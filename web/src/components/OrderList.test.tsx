import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Order } from "@/api/types";
import { createOrder } from "@/test/fixtures";
import { OrderList } from "./OrderList";
import { navigateToSessionRecovery } from "./session-navigation";

vi.mock("./session-navigation", () => ({ navigateToSessionRecovery: vi.fn() }));
beforeEach(() => vi.mocked(navigateToSessionRecovery).mockClear());

function createCanceled(order: Order) {
  return { kind: "canceled", order: { ...order, status: "CANCELED", canceledAt: "2026-10-17T10:40:00.000Z" } } as const;
}

async function cancelWith(order: Order, outcome: unknown): Promise<ReturnType<typeof vi.fn>> {
  const cancel = vi.fn().mockResolvedValue(outcome);
  render(<OrderList firstPage={{ items: [order], nextCursor: null }} cancel={cancel} loadPage={vi.fn()} />);
  await userEvent.click(screen.getByRole("button", { name: `${order.dropName} 주문 취소` }));
  return cancel;
}

const DROP_CLOSED_REJECTION = { kind: "rejected", code: "DROP_CLOSED", message: "마감한 드롭의 주문은 취소할 수 없습니다", retryAfterSeconds: undefined } as const;

describe("OrderList", () => {
  it("취소 중 재발급이 필요하면 현재 화면 기준으로 이동한다", async () => {
    await cancelWith(createOrder(), { kind: "refresh-required" });
    expect(navigateToSessionRecovery).toHaveBeenCalledOnce();
  });

  it("더 보기 중 재발급이 필요하면 현재 화면 기준으로 이동한다", async () => {
    render(<OrderList firstPage={{ items: [createOrder()], nextCursor: "cursor-1" }} cancel={vi.fn()} loadPage={vi.fn().mockResolvedValue({ kind: "refresh-required" })} />);
    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));
    expect(navigateToSessionRecovery).toHaveBeenCalledOnce();
  });

  it("취소 버튼을 누르면 그 주문 번호로 취소를 부른다", async () => {
    const order = createOrder();
    const cancel = await cancelWith(order, createCanceled(order));

    expect(cancel).toHaveBeenCalledWith(order.id);
  });

  it("취소하면 그 주문을 취소됨으로 바꾼다", async () => {
    const order = createOrder();
    await cancelWith(order, createCanceled(order));

    expect(await screen.findByText("취소됨")).toBeInTheDocument();
  });

  it("취소하면 결과를 알린다", async () => {
    const order = createOrder();
    await cancelWith(order, createCanceled(order));

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("주문을 취소했습니다"));
  });

  it("마감 뒤라 거절되면 취소 버튼을 비활성화한다", async () => {
    const order = createOrder();
    render(<OrderList firstPage={{ items: [order], nextCursor: null }} cancel={vi.fn().mockResolvedValue(DROP_CLOSED_REJECTION)} loadPage={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: `${order.dropName} 주문 취소` }));

    await waitFor(() => expect(screen.getByRole("button", { name: `${order.dropName} 주문 취소` })).toBeDisabled());
  });

  it("마감 뒤라 거절되면 주문 행에 서버 문구를 보여 준다", async () => {
    const order = createOrder();
    render(<OrderList firstPage={{ items: [order], nextCursor: null }} cancel={vi.fn().mockResolvedValue(DROP_CLOSED_REJECTION)} loadPage={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: `${order.dropName} 주문 취소` }));

    expect(await screen.findByRole("listitem")).toHaveTextContent(DROP_CLOSED_REJECTION.message);
  });

  it("503이면 알림에 Retry-After 초 뒤 다시 시도하라는 안내를 붙인다", async () => {
    const order = createOrder();
    const cancel = vi.fn().mockResolvedValue({ kind: "rejected", code: "STOCK_UNAVAILABLE", message: "재고 서버가 바쁩니다.", retryAfterSeconds: 2 });
    render(<OrderList firstPage={{ items: [order], nextCursor: null }} cancel={cancel} loadPage={vi.fn()} />);

    await userEvent.click(screen.getByRole("button", { name: `${order.dropName} 주문 취소` }));

    expect(await screen.findByText("재고 서버가 바쁩니다. 2초 뒤 다시 시도해 주세요.")).toHaveAttribute("role", "status");
  });

  it("더 보기가 503이면 다시 시도 안내를 보여 준다", async () => {
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
