import { describe, expect, it } from "vitest";
import { MOCK_USERS, MockStore } from "./mock-store";

const NOW = Date.parse("2026-10-17T10:30:00.000Z");
const buyer = MOCK_USERS["mock-USER"]!;
const admin = MOCK_USERS["mock-ADMIN"]!;

function openStoreWith(quantity: number): { store: MockStore; dropId: string } {
  const store = new MockStore(() => NOW);
  return { store, dropId: store.seedDrop("[테스트] 드롭", quantity, -1, 60).id };
}

describe("MockStore", () => {
  it("같은 사용자가 두 번 사면 ALREADY_PURCHASED로 거절한다", () => {
    const { store, dropId } = openStoreWith(5);
    store.purchase(dropId, buyer);

    expect(store.purchase(dropId, buyer)).toMatchObject({ kind: "rejected", rejection: { status: 409, code: "ALREADY_PURCHASED" } });
  });

  it("마지막 재고를 팔면 SOLD_OUT으로 바뀐다", () => {
    const { store, dropId } = openStoreWith(1);
    store.purchase(dropId, buyer);

    expect(store.getDrop(dropId)).toMatchObject({ value: { status: "SOLD_OUT", remainingQuantity: 0 } });
  });

  it("재고가 없으면 다음 구매를 SOLD_OUT으로 거절한다", () => {
    const { store, dropId } = openStoreWith(1);
    store.purchase(dropId, buyer);

    expect(store.purchase(dropId, admin)).toMatchObject({ rejection: { code: "SOLD_OUT" } });
  });

  it("취소하면 재고 하나가 돌아오고 품절 드롭은 다시 판매 중이 된다", () => {
    const { store, dropId } = openStoreWith(1);
    const bought = store.purchase(dropId, buyer);
    if (bought.kind !== "ok") throw new Error("구매가 실패했습니다");

    store.cancel(bought.value.id, buyer);

    expect(store.getDrop(dropId)).toMatchObject({ value: { status: "OPEN", remainingQuantity: 1 } });
  });

  it("오픈 시각 전 드롭은 DROP_NOT_OPEN으로 거절한다", () => {
    const store = new MockStore(() => NOW);
    const drop = store.seedDrop("[테스트] 예정", 5, 10, 60);

    expect(store.purchase(drop.id, buyer)).toMatchObject({ rejection: { code: "DROP_NOT_OPEN" } });
  });

  it("다른 사람의 주문은 취소할 수 없다", () => {
    const { store, dropId } = openStoreWith(5);
    const bought = store.purchase(dropId, buyer);
    if (bought.kind !== "ok") throw new Error("구매가 실패했습니다");

    expect(store.cancel(bought.value.id, admin)).toMatchObject({ rejection: { status: 403, code: "FORBIDDEN" } });
  });
});
