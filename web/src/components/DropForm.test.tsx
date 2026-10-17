import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DropForm } from "./DropForm";

vi.mock("next/link", () => ({ default: ({ href, children, ...rest }: { href: string; children: React.ReactNode }) => <a href={href} {...rest}>{children}</a> }));

describe("DropForm", () => {
  it("서버의 필드 오류를 해당 입력란 아래에 붙이고 입력란을 오류로 표시한다", async () => {
    const action = vi.fn().mockResolvedValue({
      kind: "rejected",
      message: "입력값을 확인해 주세요",
      fieldErrors: [{ field: "closeAt", message: "마감 시각은 오픈 시각보다 늦어야 합니다" }],
    });
    render(<DropForm action={action} />);

    await userEvent.click(screen.getByRole("button", { name: "드롭 등록" }));

    const closeAt = await screen.findByLabelText("마감 시각");
    expect(closeAt).toHaveAttribute("aria-invalid", "true");
    expect(closeAt).toHaveAccessibleDescription("마감 시각은 오픈 시각보다 늦어야 합니다");
    expect(screen.getByRole("alert")).toHaveTextContent("입력값을 확인해 주세요");
  });

  it("오류가 없는 입력란은 안내 문구만 둔다", () => {
    render(<DropForm action={vi.fn()} />);

    expect(screen.getByLabelText("초당 입장 인원")).not.toHaveAttribute("aria-invalid");
    expect(screen.getByLabelText("초당 입장 인원")).toHaveAccessibleDescription("1에서 10000, 기본 50");
  });
});
