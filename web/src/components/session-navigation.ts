import { createSessionRecoveryPath } from "@/domain/session-recovery";

export function navigateToSessionRecovery(location: Pick<Location, "href" | "assign"> = window.location): void {
  const url = new URL(location.href);
  location.assign(createSessionRecoveryPath({ path: url.pathname, search: url.searchParams, hash: url.hash }));
}
