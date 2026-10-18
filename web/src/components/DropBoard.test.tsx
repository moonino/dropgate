import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { createDrop } from "@/test/fixtures";
import { DropBoard } from "./DropBoard";

const LAST_PAGE = { kind: "ok", body: { items: [createDrop({ id: "next", name: "다음 드롭" })], nextCursor: null } } as const;
const UNAVAILABLE = { kind: "unavailable", notice: "잠시 바쁩니다. 2초 뒤 다시 시도해 주세요." } as const;

async function loadMoreWith(result: unknown): Promise<ReturnType<typeof vi.fn>> {
  const loadPage = vi.fn().mockResolvedValue(result);
  render(<DropBoard firstPage={{ items: [createDrop()], nextCursor: "cursor-1" }} loadPage={loadPage} />);
  await userEvent.click(screen.getByRole("button", { name: "더 보기" }));
  return loadPage;
}

describe("DropBoard", () => {
  it("드롭마다 한국 오픈 시각, 남은 수량, 상태 문구를 보여 준다", () => {
    render(<DropBoard firstPage={{ items: [createDrop()], nextCursor: null }} loadPage={vi.fn()} />);

    const row = screen.getByRole("listitem");
    expect(within(row).getByText("19:30")).toBeInTheDocument();
    expect(within(row).getByText("37 / 100")).toBeInTheDocument();
    expect(within(row).getByText("판매 중")).toBeInTheDocument();
  });

  it("더 보기를 누르면 다음 커서로 불러온다", async () => {
    const loadPage = await loadMoreWith(LAST_PAGE);

    expect(loadPage).toHaveBeenCalledWith("cursor-1");
  });

  it("불러온 드롭을 보드 뒤에 붙인다", async () => {
    await loadMoreWith(LAST_PAGE);

    expect(await screen.findAllByRole("listitem")).toHaveLength(2);
  });

  it("마지막 페이지를 붙이면 더 보기를 없앤다", async () => {
    await loadMoreWith(LAST_PAGE);

    await waitFor(() => expect(screen.queryByRole("button", { name: "더 보기" })).not.toBeInTheDocument());
  });

  it("더 보기가 503이면 다시 시도 안내를 보여 준다", async () => {
    await loadMoreWith(UNAVAILABLE);

    expect(await screen.findByRole("alert")).toHaveTextContent("잠시 바쁩니다. 2초 뒤 다시 시도해 주세요.");
  });

  it("더 보기가 503이어도 보드의 드롭은 그대로 둔다", async () => {
    await loadMoreWith(UNAVAILABLE);

    await screen.findByRole("alert");
    expect(screen.getAllByRole("listitem")).toHaveLength(1);
  });

  it("드롭이 없으면 빈 보드 안내를 보여 준다", () => {
    render(<DropBoard firstPage={{ items: [], nextCursor: null }} loadPage={vi.fn()} />);

    expect(screen.getByText("지금 보드에 올라온 드롭이 없습니다.")).toBeInTheDocument();
  });
});
