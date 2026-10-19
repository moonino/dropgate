"use client";

import { useEffect } from "react";
import { REFRESHED_PARAMETER } from "@/domain/session-recovery";

export function ConsumeRefreshMarker() {
  useEffect(() => {
    const location = new URL(window.location.href);
    if (!location.searchParams.has(REFRESHED_PARAMETER)) return;
    location.searchParams.delete(REFRESHED_PARAMETER);
    window.history.replaceState(window.history.state, "", location);
  }, []);
  return <></>;
}
