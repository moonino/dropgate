import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Countdown } from "./Countdown";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh }) }));

describe("Countdown", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-10-17T10:29:58.000Z"));
    refresh.mockClear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("남은 시간을 타이머 이름으로 읽을 수 있게 둔다", () => {
    render(<Countdown label="오픈까지" targetIso="2026-10-17T10:30:00.000Z" absoluteLabel="10월 17일 (토) 19:30" renderedAtMillis={Date.now()} />);

    expect(screen.getByRole("timer")).toHaveAccessibleName("오픈까지 00시간 00분 02초");
  });

  it("0이 되면 화면을 한 번만 새로 불러온다", () => {
    render(<Countdown label="오픈까지" targetIso="2026-10-17T10:30:00.000Z" absoluteLabel="10월 17일 (토) 19:30" renderedAtMillis={Date.now()} />);

    act(() => vi.advanceTimersByTime(5000));

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("status")).toHaveTextContent("시각이 됐습니다. 상태를 확인합니다");
  });

  it("10초 전에 한 번 알린다", () => {
    vi.setSystemTime(new Date("2026-10-17T10:29:49.000Z"));
    render(<Countdown label="오픈까지" targetIso="2026-10-17T10:30:00.000Z" absoluteLabel="10월 17일 (토) 19:30" renderedAtMillis={Date.now()} />);

    act(() => vi.advanceTimersByTime(1000));

    expect(screen.getByRole("status")).toHaveTextContent("10초 남았습니다");
  });
});
