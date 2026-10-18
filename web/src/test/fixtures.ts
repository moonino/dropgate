import type { Drop, Order } from "@/api/types";

export function createDrop(overrides: Partial<Drop> = {}): Drop {
  return {
    id: "0192f0a0-0000-7000-8000-000000000101",
    name: "[테스트] 스니커즈",
    totalQuantity: 100,
    openAt: "2026-10-17T10:30:00.000Z",
    closeAt: "2026-10-18T10:30:00.000Z",
    status: "OPEN",
    admissionRatePerSec: 50,
    remainingQuantity: 37,
    ...overrides,
  };
}

export function createOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: "0192f0a0-0000-7000-8000-000000000201",
    dropId: "0192f0a0-0000-7000-8000-000000000101",
    dropName: "[테스트] 스니커즈",
    quantity: 1,
    status: "CONFIRMED",
    confirmedAt: "2026-10-17T10:30:02.000Z",
    canceledAt: null,
    ...overrides,
  };
}
