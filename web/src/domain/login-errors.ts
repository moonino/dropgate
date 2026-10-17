import { describeRetryAfter } from "./retry";

const AUTH_UNAVAILABLE_MESSAGE = "로그인 서버가 잠시 응답하지 않습니다.";

const UNAVAILABLE_MESSAGES: Record<string, string> = {
  KAKAO_UNAVAILABLE: "카카오 응답이 늦어 로그인하지 못했습니다.",
  AUTH_UNAVAILABLE: AUTH_UNAVAILABLE_MESSAGE,
};

const EXPIRED_MESSAGE = "로그인 요청이 만료됐습니다. 다시 시도해 주세요.";
const FALLBACK_MESSAGE = "로그인하지 못했습니다. 다시 시도해 주세요.";

export type LoginNotice = { kind: "none" } | { kind: "error"; message: string };

export function decideLoginNotice(errorCode: string | undefined, retryAfterSeconds: number | undefined): LoginNotice {
  if (!errorCode) return { kind: "none" };
  const unavailable = UNAVAILABLE_MESSAGES[errorCode];
  if (unavailable) return { kind: "error", message: `${unavailable} ${describeRetryAfter(retryAfterSeconds)}` };
  return { kind: "error", message: errorCode === "VALIDATION_FAILED" ? EXPIRED_MESSAGE : FALLBACK_MESSAGE };
}

export function describeAuthUnavailable(retryAfterSeconds: number | undefined): string {
  return `${AUTH_UNAVAILABLE_MESSAGE} ${describeRetryAfter(retryAfterSeconds)}`;
}
