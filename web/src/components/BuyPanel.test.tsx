import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { createDrop } from "@/test/fixtures";
import { BuyPanel } from "./BuyPanel";

describe("BuyPanel", () => {
  it("로그인하지 않았으면 로그인 링크를 보여 준다", () => {
    render(<BuyPanel drop={createDrop()} signedIn={false} purchase={vi.fn()} />);

    expect(screen.getByRole("link", { name: "로그인하고 구매하기" })).toHaveAttribute("href", "/login");
  });

  it("구매가 확정되면 주문 번호와 내 주문 링크를 보여 주고 버튼을 닫는다", async () => {
    const purchase = vi.fn().mockResolvedValue({ kind: "confirmed", orderId: "0192f0a0-0000-7000-8000-0000c3a1beef", confirmedAt: "2026-10-17T10:30:02.000Z" });
    render(<BuyPanel drop={createDrop()} signedIn purchase={purchase} />);

    await userEvent.click(screen.getByRole("button", { name: "구매하기" }));

    expect(await screen.findByText("구매가 확정됐습니다")).toBeInTheDocument();
    expect(screen.getByText(/C3A1BEEF/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "구매 완료" })).toBeDisabled();
  });

  it("서버가 품절이라고 답하면 서버 문구를 그대로 보여 주고 버튼을 품절로 닫는다", async () => {
    const purchase = vi.fn().mockResolvedValue({ kind: "rejected", code: "SOLD_OUT", message: "재고가 모두 소진됐습니다", retryAfterSeconds: undefined });
    render(<BuyPanel drop={createDrop()} signedIn purchase={purchase} />);

    await userEvent.click(screen.getByRole("button", { name: "구매하기" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("재고가 모두 소진됐습니다");
    expect(screen.getByRole("button", { name: "품절" })).toBeDisabled();
  });

  it("503이면 서버 문구에 Retry-After 초 뒤 다시 시도하라는 안내를 붙인다", async () => {
    const purchase = vi.fn().mockResolvedValue({ kind: "rejected", code: "STOCK_UNAVAILABLE", message: "재고 서버가 바쁩니다.", retryAfterSeconds: 2 });
    render(<BuyPanel drop={createDrop()} signedIn purchase={purchase} />);

    await userEvent.click(screen.getByRole("button", { name: "구매하기" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("재고 서버가 바쁩니다. 2초 뒤 다시 시도해 주세요.");
  });

  it("오픈 전 드롭은 오픈 시각을 적은 닫힌 버튼을 보여 준다", () => {
    render(<BuyPanel drop={createDrop({ status: "SCHEDULED" })} signedIn purchase={vi.fn()} />);

    expect(screen.getByRole("button", { name: "19:30 오픈" })).toBeDisabled();
  });
});
