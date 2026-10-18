import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SaleOpenAnnouncement } from "./SaleOpenAnnouncement";

describe("SaleOpenAnnouncement", () => {
  it("오픈 예정에서 판매 중으로 바뀌면 판매가 열렸다고 알린다", () => {
    const { rerender } = render(<SaleOpenAnnouncement status="SCHEDULED" />);

    rerender(<SaleOpenAnnouncement status="OPEN" />);

    expect(screen.getByRole("status")).toHaveTextContent("판매가 열렸습니다");
  });

  it("처음부터 판매 중이면 알리지 않는다", () => {
    render(<SaleOpenAnnouncement status="OPEN" />);

    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });
});
