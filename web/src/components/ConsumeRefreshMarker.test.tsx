import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { createSessionRecoveryPath } from "@/domain/session-recovery";
import { ConsumeRefreshMarker } from "./ConsumeRefreshMarker";

describe("완료한 재발급 표시 소비", () => {
  it("재발급 값이0이면 표시를 소비하지 않는다", () => {
    window.history.replaceState({}, "", "/orders?refreshed=0&cursor=next#row");
    render(<ConsumeRefreshMarker />);
    expect(window.location.pathname + window.location.search + window.location.hash).toBe("/orders?refreshed=0&cursor=next#row");
  });

  it("재발급 표시만 지우고 다른 query와 hash를 보존한다", () => {
    window.history.replaceState({}, "", "/orders?refreshed=1&cursor=next#row");
    render(<ConsumeRefreshMarker />);
    expect(window.location.pathname + window.location.search + window.location.hash).toBe("/orders?cursor=next#row");
  });

  it("성공한 뒤 다음 만료에는 다시 재발급할 수 있다", () => {
    window.history.replaceState({}, "", "/orders?refreshed=1");
    render(<ConsumeRefreshMarker />);
    expect(createSessionRecoveryPath({ path: window.location.pathname, search: new URLSearchParams(window.location.search) })).toBe("/session/refresh?next=%2Forders");
  });
});
