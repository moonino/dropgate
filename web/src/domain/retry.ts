const RETRY_LATER = "잠시 뒤 다시 시도해 주세요.";

export function parseRetryAfterSeconds(raw: string | null | undefined): number | undefined {
  const seconds = Number(raw);
  return Number.isInteger(seconds) && seconds > 0 ? seconds : undefined;
}

export function describeRetryAfter(retryAfterSeconds: number | undefined): string {
  return retryAfterSeconds === undefined ? RETRY_LATER : `${retryAfterSeconds}초 뒤 다시 시도해 주세요.`;
}

export function appendRetryAfter(message: string, retryAfterSeconds: number | undefined): string {
  return retryAfterSeconds === undefined ? message : `${message} ${describeRetryAfter(retryAfterSeconds)}`;
}
