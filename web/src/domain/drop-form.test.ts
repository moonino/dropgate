import { describe, expect, it } from "vitest";
import { groupFieldErrors, parseDropForm } from "./drop-form";

const VALID_FIELDS = { name: "[테스트] 드롭", totalQuantity: "100", admissionRatePerSec: "50", openAt: "2026-10-20T19:00", closeAt: "2026-10-21T19:00" };

describe("parseDropForm", () => {
  it("유효한 입력을 드롭 생성 요청 본문으로 바꾼다", () => {
    expect(parseDropForm(VALID_FIELDS)).toEqual({
      kind: "valid",
      request: { name: "[테스트] 드롭", totalQuantity: 100, admissionRatePerSec: 50, openAt: "2026-10-20T10:00:00.000Z", closeAt: "2026-10-21T10:00:00.000Z" },
    });
  });

  it("비어 있는 시각과 숫자가 아닌 수량을 필드별로 알려 준다", () => {
    expect(parseDropForm({ ...VALID_FIELDS, totalQuantity: "백", closeAt: "" })).toEqual({
      kind: "invalid",
      fieldErrors: [
        { field: "totalQuantity", message: "숫자를 입력해 주세요" },
        { field: "closeAt", message: "시각을 입력해 주세요" },
      ],
    });
  });

  it("이름과 시각 순서 같은 규칙 검사는 서버에 맡긴다", () => {
    expect(parseDropForm({ ...VALID_FIELDS, name: " ", closeAt: "2026-10-19T19:00" }).kind).toBe("valid");
  });
});

describe("groupFieldErrors", () => {
  it("필드 이름으로 메시지를 찾을 수 있게 묶는다", () => {
    expect(groupFieldErrors([{ field: "closeAt", message: "늦어야 합니다" }])).toEqual({ closeAt: "늦어야 합니다" });
  });
});
