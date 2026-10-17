import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { DropFormState } from "@/app/admin/drops/new/actions";
import { DropForm } from "./DropForm";

vi.mock("next/link", () => ({ default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a> }));

const SUBMITTED_VALUES = { name: "가을 스니커즈", totalQuantity: "100", admissionRatePerSec: "50", openAt: "", closeAt: "" };

function rejectWith(overrides: Partial<Extract<DropFormState, { kind: "rejected" }>>) {
  return vi.fn().mockResolvedValue({ kind: "rejected", message: "입력값을 확인해 주세요", fieldErrors: [], retryAfterSeconds: undefined, values: SUBMITTED_VALUES, ...overrides });
}

describe("DropForm", () => {
  it("서버의 필드 오류를 해당 입력란 아래에 붙이고 입력란을 오류로 표시한다", async () => {
    render(<DropForm action={rejectWith({ fieldErrors: [{ field: "closeAt", message: "마감 시각은 오픈 시각보다 늦어야 합니다" }] })} />);

    await userEvent.click(screen.getByRole("button", { name: "드롭 등록" }));

    const closeAt = await screen.findByLabelText("마감 시각");
    expect(closeAt).toHaveAttribute("aria-invalid", "true");
    expect(closeAt).toHaveAccessibleDescription("마감 시각은 오픈 시각보다 늦어야 합니다");
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

  it("오류가 없는 입력란은 안내 문구만 둔다", () => {
    render(<DropForm action={vi.fn()} />);

    expect(screen.getByLabelText("초당 입장 인원")).not.toHaveAttribute("aria-invalid");
    expect(screen.getByLabelText("초당 입장 인원")).toHaveAccessibleDescription("1에서 10000, 기본 50");
  });
});
