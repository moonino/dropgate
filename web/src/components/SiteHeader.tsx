import Link from "next/link";
import { describeAuthUnavailable } from "@/domain/login-errors";
import { readServerTimeMillis } from "@/server/clock";
import { readRequestViewer } from "@/server/request-session";
import { isAdmin, type Viewer } from "@/server/viewer";
import { KstClock } from "./KstClock";
import { NavLinks, type NavItem } from "./NavLinks";

const BASE_ITEMS: NavItem[] = [
  { href: "/drops", label: "드롭" },
  { href: "/orders", label: "내 주문" },
];

const ADMIN_ITEM: NavItem = { href: "/admin/drops/new", label: "관리자" };

export async function SiteHeader() {
  const viewer = await readRequestViewer();
  return (
    <header className="site-header">
      <div className="site-header__left">
        <Link href="/drops" className="wordmark">
          DROPGATE
        </Link>
        <NavLinks items={isAdmin(viewer) ? [...BASE_ITEMS, ADMIN_ITEM] : BASE_ITEMS} />
      </div>
      <div className="site-header__right">
        <KstClock renderedAtMillis={readServerTimeMillis()} />
        <ViewerControls viewer={viewer} />
      </div>
    </header>
  );
}

function ViewerControls({ viewer }: { viewer: Viewer }) {
  if (viewer.kind === "unavailable") {
    return (
      <p role="status" className="muted" style={{ margin: 0, fontSize: 13 }}>
        {describeAuthUnavailable(viewer.retryAfterSeconds)}
      </p>
    );
  }
  if (viewer.kind === "anonymous") {
    return (
      <Link href="/login" className="button button--small">
        로그인
      </Link>
    );
  }
  return (
    <form action="/session/logout" method="post" style={{ display: "flex", alignItems: "center", gap: 16 }}>
      <span style={{ fontSize: 14 }}>{viewer.user.nickname}</span>
      <button type="submit" className="button button--small">
        로그아웃
      </button>
    </form>
  );
}
