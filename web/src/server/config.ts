export type BackendService = "auth" | "order" | "notification";

const BASE_URL_VARIABLES: Record<BackendService, string> = {
  auth: "DROPGATE_WEB_AUTH_BASE_URL",
  order: "DROPGATE_WEB_ORDER_BASE_URL",
  notification: "DROPGATE_WEB_NOTIFICATION_BASE_URL",
};

const MOCK_BASE_URLS: Record<BackendService, string> = {
  auth: "http://auth.mock",
  order: "http://order.mock",
  notification: "http://notification.mock",
};

export function isMockMode(): boolean {
  return process.env.NODE_ENV !== "production" && process.env.DROPGATE_WEB_MOCK === "true";
}

export function readBaseUrl(service: BackendService): string {
  if (isMockMode()) return MOCK_BASE_URLS[service];
  const variable = BASE_URL_VARIABLES[service];
  const value = process.env[variable];
  if (!value) throw new Error(`${variable} 환경 변수가 없습니다`);
  return value.replace(/\/$/, "");
}

export function readMockBaseUrl(service: BackendService): string {
  return MOCK_BASE_URLS[service];
}
