import { BackendError } from "./backend-error";
import { isMockMode, readBaseUrl, type BackendService } from "./config";

export type BackendRequest = {
  method?: "GET" | "POST" | "DELETE";
  body?: unknown;
  accessToken?: string;
  cookieHeader?: string;
};

export async function callBackend<T>(service: BackendService, path: string, request: BackendRequest = {}): Promise<T> {
  const response = await sendBackendRequest(service, path, request);
  if (!response.ok) throw await BackendError.fromResponse(response);
  return (await response.json()) as T;
}

export async function sendBackendRequest(service: BackendService, path: string, request: BackendRequest): Promise<Response> {
  const outgoing = createRequest(`${readBaseUrl(service)}${path}`, request);
  if (isMockMode()) {
    const { sendMockRequest } = await import("./mock/mock-transport");
    return sendMockRequest(outgoing);
  }
  return fetch(outgoing, { cache: "no-store" });
}

function createRequest(url: string, request: BackendRequest): Request {
  const headers = new Headers({ Accept: "application/json" });
  if (request.accessToken) headers.set("Authorization", `Bearer ${request.accessToken}`);
  if (request.body !== undefined) headers.set("Content-Type", "application/json");
  if (request.cookieHeader) headers.set("Cookie", request.cookieHeader);
  return new Request(url, {
    method: request.method ?? "GET",
    headers,
    body: request.body === undefined ? undefined : JSON.stringify(request.body),
    redirect: "manual",
  });
}
