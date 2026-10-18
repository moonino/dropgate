"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export type NavItem = { href: string; label: string };

export function NavLinks({ items }: { items: NavItem[] }) {
  const pathname = usePathname();
  return (
    <nav aria-label="주 메뉴" className="nav">
      {items.map((item) => (
        <Link key={item.href} href={item.href} className="nav__link" aria-current={pathname.startsWith(item.href) ? "page" : undefined}>
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
