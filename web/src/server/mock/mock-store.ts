import type { Drop, Order, User } from "@/api/types";

export const MOCK_USERS: Record<string, User> = {
  "mock-USER": { id: "0192f0a0-0000-7000-8000-000000000001", nickname: "모의 사용자", role: "USER" },
  "mock-ADMIN": { id: "0192f0a0-0000-7000-8000-000000000002", nickname: "모의 관리자", role: "ADMIN" },
};

export type MockRejection = { status: number; code: string; message: string };

export type MockResult<T> = { kind: "ok"; value: T } | { kind: "rejected"; rejection: MockRejection };

export type NewDrop = Pick<Drop, "name" | "totalQuantity" | "admissionRatePerSec" | "openAt" | "closeAt">;

type StoredOrder = Order & { userId: string };

const MINUTE_MILLIS = 60_000;

export class MockStore {
  private readonly drops: Drop[] = [];
  private readonly orders: StoredOrder[] = [];
  private sequence = 0;

  constructor(private readonly now: () => number) {}

  listDrops(): Drop[] {
    return this.drops
      .map((drop) => this.advanceStatus(drop))
      .filter((drop) => drop.status !== "CLOSED")
      .sort((left, right) => left.openAt.localeCompare(right.openAt) || left.id.localeCompare(right.id));
  }

  getDrop(dropId: string): MockResult<Drop> {
    const drop = this.drops.find((candidate) => candidate.id === dropId);
    return drop ? { kind: "ok", value: this.advanceStatus(drop) } : reject(404, "DROP_NOT_FOUND", "드롭을 찾을 수 없습니다");
  }

  createDrop(newDrop: NewDrop): Drop {
    const drop: Drop = {
      id: this.createNextId(),
      name: newDrop.name,
      totalQuantity: newDrop.totalQuantity,
      openAt: newDrop.openAt,
      closeAt: newDrop.closeAt,
      status: "SCHEDULED",
      admissionRatePerSec: newDrop.admissionRatePerSec,
      remainingQuantity: newDrop.totalQuantity,
    };
    this.drops.push(drop);
    return drop;
  }

  seedDrop(name: string, totalQuantity: number, opensInMinutes: number, closesInMinutes: number): Drop {
    return this.createDrop({
      name,
      totalQuantity,
      openAt: new Date(this.now() + opensInMinutes * MINUTE_MILLIS).toISOString(),
      closeAt: new Date(this.now() + closesInMinutes * MINUTE_MILLIS).toISOString(),
      admissionRatePerSec: 50,
    });
  }

  purchase(dropId: string, user: User): MockResult<Order> {
    const found = this.getDrop(dropId);
    if (found.kind === "rejected") return found;
    const drop = found.value;
    if (drop.status === "SCHEDULED" || drop.status === "CLOSED") return reject(409, "DROP_NOT_OPEN", "판매 중인 드롭이 아닙니다");
    if ((drop.remainingQuantity ?? 0) <= 0) return reject(409, "SOLD_OUT", "재고가 모두 소진됐습니다");
    if (this.orders.some((order) => order.dropId === dropId && order.userId === user.id)) {
      return reject(409, "ALREADY_PURCHASED", "이미 구매한 드롭입니다");
    }
    drop.remainingQuantity = (drop.remainingQuantity ?? 0) - 1;
    if (drop.remainingQuantity === 0) drop.status = "SOLD_OUT";
    const order: StoredOrder = {
      id: this.createNextId(),
      dropId,
      dropName: drop.name,
      quantity: 1,
      status: "CONFIRMED",
      confirmedAt: new Date(this.now()).toISOString(),
      canceledAt: null,
      userId: user.id,
    };
    this.orders.push(order);
    return { kind: "ok", value: stripUser(order) };
  }

  cancel(orderId: string, user: User): MockResult<Order> {
    const order = this.orders.find((candidate) => candidate.id === orderId);
    if (!order) return reject(404, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다");
    if (order.userId !== user.id) return reject(403, "FORBIDDEN", "본인 주문만 취소할 수 있습니다");
    if (order.status === "CANCELED") return reject(409, "ORDER_ALREADY_CANCELED", "이미 취소한 주문입니다");
    const found = this.getDrop(order.dropId);
    if (found.kind === "rejected" || found.value.status === "CLOSED") return reject(409, "DROP_CLOSED", "마감한 드롭의 주문은 취소할 수 없습니다");
    const drop = found.value;
    order.status = "CANCELED";
    order.canceledAt = new Date(this.now()).toISOString();
    drop.remainingQuantity = (drop.remainingQuantity ?? 0) + 1;
    if (drop.status === "SOLD_OUT") drop.status = "OPEN";
    return { kind: "ok", value: stripUser(order) };
  }

  listOrders(user: User): Order[] {
    return this.orders
      .filter((order) => order.userId === user.id)
      .sort((left, right) => right.confirmedAt.localeCompare(left.confirmedAt) || right.id.localeCompare(left.id))
      .map(stripUser);
  }

  private advanceStatus(drop: Drop): Drop {
    const now = this.now();
    if (drop.status !== "CLOSED" && now >= Date.parse(drop.closeAt)) drop.status = "CLOSED";
    else if (drop.status === "SCHEDULED" && now >= Date.parse(drop.openAt)) drop.status = "OPEN";
    return drop;
  }

  private createNextId(): string {
    this.sequence += 1;
    return `0192f0a0-0000-7000-8000-${String(this.sequence).padStart(12, "0")}`;
  }
}

function reject<T>(status: number, code: string, message: string): MockResult<T> {
  return { kind: "rejected", rejection: { status, code, message } };
}

function stripUser(order: StoredOrder): Order {
  return {
    id: order.id,
    dropId: order.dropId,
    dropName: order.dropName,
    quantity: order.quantity,
    status: order.status,
    confirmedAt: order.confirmedAt,
    canceledAt: order.canceledAt,
  };
}

export function createDemoStore(now: () => number): MockStore {
  const store = new MockStore(now);
  store.seedDrop("[모의] 스니커즈 한정판", 100, -10, 24 * 60);
  store.seedDrop("[모의] 리미티드 머그", 3, -30, 6 * 60);
  store.seedDrop("[모의] 아티스트 포스터", 50, 2, 24 * 60);
  store.seedDrop("[모의] 키보드 키캡 세트", 200, 26 * 60, 48 * 60);
  return store;
}
