import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { createDrop } from "@/test/fixtures";
import { DropBoard } from "./DropBoard";

describe("DropBoard", () => {
  it("드롭마다 한국 오픈 시각, 남은 수량, 상태 문구를 보여 준다", () => {
    render(<DropBoard firstPage={{ items: [createDrop()], nextCursor: null }} loadPage={vi.fn()} />);

    const row = screen.getByRole("listitem");
    expect(within(row).getByText("19:30")).toBeInTheDocument();
    expect(within(row).getByText("37 / 100")).toBeInTheDocument();
    expect(within(row).getByText("판매 중")).toBeInTheDocument();
  });

  it("더 보기를 누르면 다음 커서로 불러와 뒤에 붙인다", async () => {
    const loadPage = vi.fn().mockResolvedValue({ kind: "ok", body: { items: [createDrop({ id: "next", name: "다음 드롭" })], nextCursor: null } });
    render(<DropBoard firstPage={{ items: [createDrop()], nextCursor: "cursor-1" }} loadPage={loadPage} />);

    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    expect(loadPage).toHaveBeenCalledWith("cursor-1");
    expect(await screen.findByText("다음 드롭")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "더 보기" })).not.toBeInTheDocument();
  });

  it("더 보기가 503이면 보드를 두고 다시 시도 안내를 보여 준다", async () => {
    const loadPage = vi.fn().mockResolvedValue({ kind: "unavailable", notice: "잠시 바쁩니다. 2초 뒤 다시 시도해 주세요." });
    render(<DropBoard firstPage={{ items: [createDrop()], nextCursor: "cursor-1" }} loadPage={loadPage} />);

    await userEvent.click(screen.getByRole("button", { name: "더 보기" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("잠시 바쁩니다. 2초 뒤 다시 시도해 주세요.");
    expect(screen.getByRole("listitem")).toBeInTheDocument();
  });

  it("드롭이 없으면 빈 보드 안내를 보여 준다", () => {
    render(<DropBoard firstPage={{ items: [], nextCursor: null }} loadPage={vi.fn()} />);

    expect(screen.getByText("지금 보드에 올라온 드롭이 없습니다.")).toBeInTheDocument();
  });
});
