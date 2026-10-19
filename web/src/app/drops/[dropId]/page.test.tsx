import { render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { callBackendOrUnavailable } from "@/server/backend";
import { readRequestViewer } from "@/server/request-session";
import { createDrop } from "@/test/fixtures";
import DropDetailPage from "./page";

vi.mock("@/server/backend", () => ({ callBackendOrUnavailable: vi.fn() }));
vi.mock("@/server/request-session", () => ({ readRequestViewer: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }), notFound: vi.fn() }));
vi.mock("./actions", () => ({ purchaseDrop: vi.fn() }));

beforeEach(() => {
  window.history.replaceState({}, "", "/drops/drop-1?refreshed=1&tab=info#buy");
  vi.mocked(callBackendOrUnavailable).mockResolvedValue({ kind: "ok", body: createDrop() });
  vi.mocked(readRequestViewer).mockResolvedValue({ kind: "signed-in", user: { id: "user-1", nickname: "구매자", role: "USER" }, accessToken: "valid" });
});

async function renderPage(): Promise<void> {
  render(await DropDetailPage({ params: Promise.resolve({ dropId: "drop-1" }) }));
}

describe("구매 복귀 표시 소비", () => {
  it("상세 조회와 인증이 성공하면 재발급 표시를 소비한다", async () => {
    await renderPage();
    expect(window.location.search).toBe("?tab=info");
  });

  it.each([{ kind: "anonymous" }, { kind: "expired" }, { kind: "unavailable", retryAfterSeconds: undefined }] as const)("인증이 $kind이면 재발급 표시를 유지한다", async (viewer) => {
    vi.mocked(readRequestViewer).mockResolvedValue(viewer);
    await renderPage();
    expect(window.location.search).toBe("?refreshed=1&tab=info");
  });

  it("상세 조회가 503이면 재발급 표시를 유지한다", async () => {
    vi.mocked(callBackendOrUnavailable).mockResolvedValue({ kind: "unavailable", notice: "장애" });
    await renderPage();
    expect(window.location.search).toBe("?refreshed=1&tab=info");
  });
});
