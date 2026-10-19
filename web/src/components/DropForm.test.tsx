import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { DropFormState } from "@/app/admin/drops/new/actions";
import { DropForm } from "./DropForm";
import { navigateToSessionRecovery } from "./session-navigation";

vi.mock("./session-navigation", () => ({ navigateToSessionRecovery: vi.fn() }));

vi.mock("next/link", () => ({ default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a> }));

const SUBMITTED_VALUES = { name: "가을 스니커즈", totalQuantity: "100", admissionRatePerSec: "50", openAt: "", closeAt: "" };

function rejectWith(overrides: Partial<Extract<DropFormState, { kind: "rejected" }>>) {
  return vi.fn().mockResolvedValue({ kind: "rejected", message: "입력값을 확인해 주세요", fieldErrors: [], retryAfterSeconds: undefined, values: SUBMITTED_VALUES, ...overrides });
}

const CLOSE_AT_ERROR = { field: "closeAt", message: "마감 시각은 오픈 시각보다 늦어야 합니다" };

async function submitWith(fieldError: typeof CLOSE_AT_ERROR): Promise<void> {
  render(<DropForm action={rejectWith({ fieldErrors: [fieldError] })} />);
  await userEvent.click(screen.getByRole("button", { name: "드롭 등록" }));
}

describe("DropForm", () => {
  it("등록 중 재발급이 필요하면 현재 화면 기준으로 이동한다", async () => {
    render(<DropForm action={vi.fn().mockResolvedValue({ kind: "refresh-required" })} />);
    await userEvent.click(screen.getByRole("button", { name: "드롭 등록" }));
    await waitFor(() => expect(navigateToSessionRecovery).toHaveBeenCalledOnce());
  });

  it("서버의 필드 오류를 해당 입력란 아래 설명으로 붙인다", async () => {
    await submitWith(CLOSE_AT_ERROR);

    await waitFor(() => expect(screen.getByLabelText("마감 시각")).toHaveAccessibleDescription(CLOSE_AT_ERROR.message));
  });

  it("서버가 필드 오류를 준 입력란을 오류로 표시한다", async () => {
    await submitWith(CLOSE_AT_ERROR);

    await waitFor(() => expect(screen.getByLabelText("마감 시각")).toHaveAttribute("aria-invalid", "true"));
  });

  it("서버 message를 폼 위 경고로 보여 준다", async () => {
    render(<DropForm action={rejectWith({})} />);

    await userEvent.click(screen.getByRole("button", { name: "드롭 등록" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("입력값을 확인해 주세요");
  });

  it("503이면 경고에 Retry-After 초 뒤 다시 시도하라는 안내를 붙인다", async () => {
    render(<DropForm action={rejectWith({ message: "잠시 바쁩니다.", retryAfterSeconds: 2 })} />);

    await userEvent.click(screen.getByRole("button", { name: "드롭 등록" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("잠시 바쁩니다. 2초 뒤 다시 시도해 주세요.");
  });

  it("거절된 뒤에도 입력한 값을 입력란에 남긴다", async () => {
    render(<DropForm action={rejectWith({})} />);

    await userEvent.type(screen.getByLabelText("이름"), SUBMITTED_VALUES.name);
    await userEvent.click(screen.getByRole("button", { name: "드롭 등록" }));

    await screen.findByRole("alert");
    expect(screen.getByLabelText("이름")).toHaveValue(SUBMITTED_VALUES.name);
  });

  it("오류가 없는 입력란은 오류로 표시하지 않는다", () => {
    render(<DropForm action={vi.fn()} />);

    expect(screen.getByLabelText("초당 입장 인원")).not.toHaveAttribute("aria-invalid");
  });

  it("오류가 없는 입력란은 안내 문구를 설명으로 둔다", () => {
    render(<DropForm action={vi.fn()} />);

    expect(screen.getByLabelText("초당 입장 인원")).toHaveAccessibleDescription("1에서 10000, 기본 50");
  });
});
