import type { TokenPair, User } from "@/api/types";
import type { MockResult } from "./mock-store";

const ACCESS_TOKEN_SECONDS = 900;
const UNAUTHENTICATED = { kind: "rejected", rejection: { status: 401, code: "UNAUTHENTICATED", message: "로그인이 필요합니다" } } as const;

export class MockSessionStore {
  private readonly access = new Map<string, User>();
  private readonly refresh = new Map<string, User>();

  constructor(private readonly createToken: () => string) {}

  issueTokenPair(user: User): TokenPair {
    const accessToken = `mock-access-${this.createToken()}`;
    const refreshToken = `mock-refresh-${this.createToken()}`;
    this.access.set(accessToken, user);
    this.refresh.set(refreshToken, user);
    return { accessToken, refreshToken, expiresIn: ACCESS_TOKEN_SECONDS, tokenType: "Bearer" };
  }

  authenticate(token: string): MockResult<User> {
    const user = this.access.get(token);
    return user ? { kind: "ok", value: user } : UNAUTHENTICATED;
  }

  rotateTokenPair(token: string): MockResult<TokenPair> {
    const user = this.refresh.get(token);
    if (!user) return UNAUTHENTICATED;
    this.refresh.delete(token);
    return { kind: "ok", value: this.issueTokenPair(user) };
  }

  logout(token: string): MockResult<void> {
    const user = this.access.get(token);
    if (!user) return UNAUTHENTICATED;
    this.access.delete(token);
    for (const [refreshToken, owner] of this.refresh) {
      if (owner.id === user.id) this.refresh.delete(refreshToken);
    }
    return { kind: "ok", value: undefined };
  }
}

const SESSIONS_KEY = Symbol.for("dropgate.mock-sessions");
type MockGlobal = typeof globalThis & { [SESSIONS_KEY]?: MockSessionStore };

export function readMockSessions(): MockSessionStore {
  const shared = globalThis as MockGlobal;
  shared[SESSIONS_KEY] ??= new MockSessionStore(() => crypto.randomUUID());
  return shared[SESSIONS_KEY];
}
