import { http, HttpResponse, type HttpHandler } from "msw";
import type { CreateDropRequest, FieldError, User } from "@/api/types";
import { readMockBaseUrl } from "../config";
import { MOCK_USERS, type MockRejection, type MockResult, type MockStore } from "./mock-store";

const DEFAULT_PAGE_LIMIT = 20;
const MAX_NAME_LENGTH = 100;
const MAX_ADMISSION_RATE = 10_000;

export function createMockHandlers(store: MockStore): HttpHandler[] {
  const auth = readMockBaseUrl("auth");
  const order = readMockBaseUrl("order");
  return [
    http.get(`${auth}/auth/me`, ({ request }) => withUser(request, (user) => HttpResponse.json(user))),
    http.get(`${order}/api/drops`, ({ request }) => HttpResponse.json(paginate(store.listDrops(), new URL(request.url)))),
    http.get(`${order}/api/drops/:dropId`, ({ params }) => respond(store.getDrop(String(params.dropId)))),
    http.post(`${order}/api/drops/:dropId/orders`, ({ request, params }) =>
      withUser(request, (user) => respond(store.purchase(String(params.dropId), user), 201)),
    ),
    http.post(`${order}/api/orders/:orderId/cancel`, ({ request, params }) =>
      withUser(request, (user) => respond(store.cancel(String(params.orderId), user))),
    ),
    http.get(`${order}/api/orders/me`, ({ request }) =>
      withUser(request, (user) => HttpResponse.json(paginate(store.listOrders(user), new URL(request.url)))),
    ),
    http.post(`${order}/api/admin/drops`, async ({ request }) => {
      const found = readUser(request);
      if (found.kind === "rejected") return rejection(found.rejection);
      if (found.value.role !== "ADMIN") return rejection({ status: 403, code: "FORBIDDEN", message: "관리자만 할 수 있습니다" });
      const body = (await request.json()) as CreateDropRequest;
      const errors = validateDrop(body);
      if (errors.length > 0) return HttpResponse.json({ code: "VALIDATION_FAILED", message: "입력값을 확인해 주세요", errors }, { status: 400 });
      return HttpResponse.json(store.createDrop(body), { status: 201 });
    }),
  ];
}

function readUser(request: Request): MockResult<User> {
  const token = request.headers.get("Authorization")?.replace(/^Bearer /, "") ?? "";
  const user = MOCK_USERS[token];
  return user ? { kind: "ok", value: user } : { kind: "rejected", rejection: { status: 401, code: "UNAUTHENTICATED", message: "로그인이 필요합니다" } };
}

function withUser(request: Request, handle: (user: User) => Response): Response {
  const found = readUser(request);
  return found.kind === "ok" ? handle(found.value) : rejection(found.rejection);
}

function respond<T>(result: MockResult<T>, successStatus = 200): Response {
  return result.kind === "ok" ? HttpResponse.json(result.value as object, { status: successStatus }) : rejection(result.rejection);
}

function rejection({ status, code, message }: MockRejection): Response {
  return HttpResponse.json({ code, message, errors: [] }, { status });
}

function paginate<T extends { id: string }>(items: T[], url: URL): { items: T[]; nextCursor: string | null } {
  const limit = Number(url.searchParams.get("limit") ?? DEFAULT_PAGE_LIMIT);
  const cursor = url.searchParams.get("cursor");
  const start = cursor ? items.findIndex((item) => item.id === cursor) + 1 : 0;
  const page = items.slice(start, start + limit);
  const hasMore = start + limit < items.length;
  return { items: page, nextCursor: hasMore ? (page.at(-1)?.id ?? null) : null };
}

function validateDrop(body: CreateDropRequest): FieldError[] {
  const errors: FieldError[] = [];
  if (!body.name?.trim() || body.name.length > MAX_NAME_LENGTH) errors.push({ field: "name", message: "이름은 1자 이상 100자 이하여야 합니다" });
  if (!Number.isInteger(body.totalQuantity) || body.totalQuantity < 1) errors.push({ field: "totalQuantity", message: "총수량은 1 이상이어야 합니다" });
  const rate = body.admissionRatePerSec;
  if (!Number.isInteger(rate) || rate < 1 || rate > MAX_ADMISSION_RATE) {
    errors.push({ field: "admissionRatePerSec", message: "초당 입장 인원은 1 이상 10000 이하여야 합니다" });
  }
  if (!(Date.parse(body.closeAt) > Date.parse(body.openAt))) errors.push({ field: "closeAt", message: "마감 시각은 오픈 시각보다 늦어야 합니다" });
  return errors;
}
