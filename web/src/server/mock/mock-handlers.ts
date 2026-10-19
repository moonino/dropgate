import { http, HttpResponse, type HttpHandler } from "msw";
import type { CreateDropRequest, FieldError, User } from "@/api/types";
import { readMockBaseUrl } from "../config";
import { type MockRejection, type MockResult, type MockStore, type NewDrop } from "./mock-store";
import type { MockSessionStore } from "./mock-session-store";

const DEFAULT_PAGE_LIMIT = 20;
const DEFAULT_ADMISSION_RATE = 50;
const MAX_NAME_LENGTH = 100;
const MAX_ADMISSION_RATE = 10_000;

export function createMockHandlers(store: MockStore, sessions: MockSessionStore): HttpHandler[] {
  const auth = readMockBaseUrl("auth");
  const order = readMockBaseUrl("order");
  return [
    http.get(`${auth}/auth/me`, ({ request }) => respondAsUser(request, sessions, (user) => HttpResponse.json(user))),
    http.post(`${auth}/auth/token/refresh`, async ({ request }) => {
      const token = readRefreshToken(await request.json());
      if (token === undefined) {
        return HttpResponse.json({ code: "VALIDATION_FAILED", message: "입력값이 올바르지 않습니다", errors: [{ field: "refreshToken", message: "필수 값이 없습니다" }] }, { status: 400 });
      }
      return respond(sessions.rotateTokenPair(token));
    }),
    http.post(`${auth}/auth/logout`, ({ request }) => {
      const result = sessions.logout(readBearerToken(request));
      return result.kind === "ok" ? new HttpResponse(null, { status: 204 }) : respondWithRejection(result.rejection);
    }),
    http.get(`${order}/api/drops`, ({ request }) => HttpResponse.json(paginate(store.listDrops(), new URL(request.url)))),
    http.get(`${order}/api/drops/:dropId`, ({ params }) => respond(store.getDrop(String(params.dropId)))),
    http.post(`${order}/api/drops/:dropId/orders`, ({ request, params }) =>
      respondAsUser(request, sessions, (user) => respond(store.purchase(String(params.dropId), user), 201)),
    ),
    http.post(`${order}/api/orders/:orderId/cancel`, ({ request, params }) =>
      respondAsUser(request, sessions, (user) => respond(store.cancel(String(params.orderId), user))),
    ),
    http.get(`${order}/api/orders/me`, ({ request }) =>
      respondAsUser(request, sessions, (user) => HttpResponse.json(paginate(store.listOrders(user), new URL(request.url)))),
    ),
    http.post(`${order}/api/admin/drops`, async ({ request }) => {
      const found = authenticateRequest(request, sessions);
      if (found.kind === "rejected") return respondWithRejection(found.rejection);
      if (found.value.role !== "ADMIN") return respondWithRejection({ status: 403, code: "FORBIDDEN", message: "관리자만 할 수 있습니다" });
      const body = (await request.json()) as CreateDropRequest;
      const errors = validateDrop(body);
      if (errors.length > 0) return HttpResponse.json({ code: "VALIDATION_FAILED", message: "입력값을 확인해 주세요", errors }, { status: 400 });
      return HttpResponse.json(store.createDrop(toNewDrop(body)), { status: 201 });
    }),
  ];
}

function readBearerToken(request: Request): string {
  return request.headers.get("Authorization")?.replace(/^Bearer /, "") ?? "";
}

function readRefreshToken(body: unknown): string | undefined {
  if (typeof body !== "object" || body === null || !("refreshToken" in body)) return undefined;
  return typeof body.refreshToken === "string" ? body.refreshToken : undefined;
}

function authenticateRequest(request: Request, sessions: MockSessionStore): MockResult<User> {
  return sessions.authenticate(readBearerToken(request));
}

function respondAsUser(request: Request, sessions: MockSessionStore, handle: (user: User) => Response): Response {
  const found = authenticateRequest(request, sessions);
  return found.kind === "ok" ? handle(found.value) : respondWithRejection(found.rejection);
}

function respond<T>(result: MockResult<T>, successStatus = 200): Response {
  return result.kind === "ok" ? HttpResponse.json(result.value as object, { status: successStatus }) : respondWithRejection(result.rejection);
}

function respondWithRejection({ status, code, message }: MockRejection): Response {
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

function toNewDrop(body: CreateDropRequest): NewDrop {
  return {
    name: body.name,
    totalQuantity: body.totalQuantity,
    admissionRatePerSec: body.admissionRatePerSec ?? DEFAULT_ADMISSION_RATE,
    openAt: body.openAt,
    closeAt: body.closeAt,
  };
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
