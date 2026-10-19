export const DEFAULT_RETURN_PATH = "/drops";

export function resolveSessionReturnUrl(next: string | null, requestUrl: string): URL {
  const fallback = new URL(DEFAULT_RETURN_PATH, requestUrl);
  if (!next?.startsWith("/") || next.startsWith("//")) return fallback;
  try {
    const target = new URL(next, requestUrl);
    return target.origin === fallback.origin ? target : fallback;
  } catch {
    return fallback;
  }
}
