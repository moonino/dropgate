export const REFRESHED_PARAMETER = "refreshed";
export const REFRESHED_VALUE = "1";
const SESSION_REFRESH_PATH = "/session/refresh";
const SESSION_EXPIRED_PATH = "/session/expired";

export type PageSearchParams = Record<string, string | string[] | undefined>;
export type SessionRecoveryContext = { path: string; search: URLSearchParams; hash?: string };

export function createPageRecoveryContext(path: string, values: PageSearchParams): SessionRecoveryContext {
  const search = new URLSearchParams();
  for (const [name, value] of Object.entries(values)) {
    if (value === undefined) continue;
    for (const item of [value].flat()) search.append(name, item);
  }
  return { path, search };
}

export function createSessionRecoveryPath(context: SessionRecoveryContext): string {
  if (context.search.get(REFRESHED_PARAMETER) === REFRESHED_VALUE) return SESSION_EXPIRED_PATH;
  const query = context.search.toString();
  const next = context.path + (query ? `?${query}` : "") + (context.hash ?? "");
  return `${SESSION_REFRESH_PATH}?${new URLSearchParams({ next })}`;
}
