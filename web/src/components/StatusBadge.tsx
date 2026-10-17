import type { Badge } from "@/domain/status";

const TONE_CLASSES: Record<Badge["tone"], string> = {
  outline: "badge",
  accent: "badge badge--accent",
  muted: "badge badge--muted",
  dashed: "badge badge--dashed",
};

export function StatusBadge({ badge }: { badge: Badge }) {
  return <span className={TONE_CLASSES[badge.tone]}>{badge.label}</span>;
}
