export type SessionRefreshRequired = { kind: "refresh-required" };
export type SessionActionResult<T> = T | SessionRefreshRequired;
export const SESSION_REFRESH_REQUIRED: SessionRefreshRequired = { kind: "refresh-required" };
