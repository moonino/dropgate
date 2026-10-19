import { describe, expect, it, vi } from "vitest";
import { navigateToSessionRecovery } from "./session-navigation";

describe("navigateToSessionRecovery", () => {
  it("현재 화면의 검색 조건과 해시를 담아 재발급으로 이동한다", () => {
    const assign = vi.fn();
    navigateToSessionRecovery({ href: "http://web.test/drops/drop-1?tab=info#buy", assign });
    expect(assign).toHaveBeenCalledWith("/session/refresh?next=%2Fdrops%2Fdrop-1%3Ftab%3Dinfo%23buy");
  });

  it("재발급 표시가 남아 있으면 만료 정리로 이동한다", () => {
    const assign = vi.fn();
    navigateToSessionRecovery({ href: "http://web.test/orders?refreshed=1", assign });
    expect(assign).toHaveBeenCalledWith("/session/expired");
  });
});
