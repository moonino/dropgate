import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { createDrop } from "@/test/fixtures";
import { BuyPanel } from "./BuyPanel";
import { navigateToSessionRecovery } from "./session-navigation";

vi.mock("./session-navigation", () => ({ navigateToSessionRecovery: vi.fn() }));

const CONFIRMED = { kind: "confirmed", orderId: "0192f0a0-0000-7000-8000-0000c3a1beef", confirmedAt: "2026-10-17T10:30:02.000Z" } as const;
const SOLD_OUT = { kind: "rejected", code: "SOLD_OUT", message: "재고가 모두 소진됐습니다", retryAfterSeconds: undefined } as const;

async function buyWith(outcome: unknown): Promise<void> {
  render(<BuyPanel drop={createDrop()} signedIn purchase={vi.fn().mockResolvedValue(outcome)} />);
  await userEvent.click(screen.getByRole("button", { name: "구매하기" }));
}

describe("BuyPanel", () => {
  it("재발급이 필요하면 현재 화면 기준으로 이동한다", async () => {
    await buyWith({ kind: "refresh-required" });
    expect(navigateToSessionRecovery).toHaveBeenCalledOnce();
  });

  it("로그인하지 않았으면 로그인 링크를 보여 준다", () => {
    render(<BuyPanel drop={createDrop()} signedIn={false} purchase={vi.fn()} />);

    expect(screen.getByRole("link", { name: "로그인하고 구매하기" })).toHaveAttribute("href", "/login");
  });

  it("구매가 확정되면 확정 안내에 주문 번호를 보여 준다", async () => {
    await buyWith(CONFIRMED);

    expect(await screen.findByRole("status")).toHaveTextContent(/구매가 확정됐습니다.*주문 C3A1BEEF/);
  });

  it("구매가 확정되면 내 주문으로 가는 링크를 보여 준다", async () => {
    await buyWith(CONFIRMED);

    expect(await screen.findByRole("link", { name: "내 주문에서 보기" })).toHaveAttribute("href", "/orders");
  });

  it("구매가 확정되면 버튼을 구매 완료로 닫는다", async () => {
    await buyWith(CONFIRMED);

    expect(await screen.findByRole("button", { name: "구매 완료" })).toBeDisabled();
  });

  it("서버가 품절이라고 답하면 서버 문구를 그대로 보여 준다", async () => {
    await buyWith(SOLD_OUT);

    expect(await screen.findByRole("alert")).toHaveTextContent("재고가 모두 소진됐습니다");
  });

  it("서버가 품절이라고 답하면 버튼을 품절로 닫는다", async () => {
    await buyWith(SOLD_OUT);

    expect(await screen.findByRole("button", { name: "품절" })).toBeDisabled();
  });

  it("503이면 서버 문구에 Retry-After 초 뒤 다시 시도하라는 안내를 붙인다", async () => {
    await buyWith({ kind: "rejected", code: "STOCK_UNAVAILABLE", message: "재고 서버가 바쁩니다.", retryAfterSeconds: 2 });

    expect(await screen.findByRole("alert")).toHaveTextContent("재고 서버가 바쁩니다. 2초 뒤 다시 시도해 주세요.");
  });

  it("오픈 전 드롭은 오픈 시각을 적은 닫힌 버튼을 보여 준다", () => {
    render(<BuyPanel drop={createDrop({ status: "SCHEDULED" })} signedIn purchase={vi.fn()} />);

    expect(screen.getByRole("button", { name: "19:30 오픈" })).toBeDisabled();
  });
});
