export function forwardSetCookies(from: Response, to: Response): void {
  for (const cookie of from.headers.getSetCookie()) to.headers.append("Set-Cookie", cookie);
}
