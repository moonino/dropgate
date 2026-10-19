import { getResponse, type HttpHandler } from "msw";
import { createMockHandlers } from "./mock-handlers";
import { createDemoStore } from "./mock-store";
import { readMockSessions } from "./mock-session-store";

const HANDLERS_KEY = Symbol.for("dropgate.mock-handlers");
const NOT_IMPLEMENTED = 501;

type MockGlobal = typeof globalThis & { [HANDLERS_KEY]?: HttpHandler[] };

export async function sendMockRequest(request: Request): Promise<Response> {
  const response = await getResponse(readDemoHandlers(), request);
  return response ?? Response.json({ code: "UNKNOWN", message: "모의 응답이 없는 요청입니다", errors: [] }, { status: NOT_IMPLEMENTED });
}

function readDemoHandlers(): HttpHandler[] {
  const shared = globalThis as MockGlobal;
  shared[HANDLERS_KEY] ??= createMockHandlers(createDemoStore(Date.now), readMockSessions());
  return shared[HANDLERS_KEY];
}
