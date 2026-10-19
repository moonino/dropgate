"use client";

import { useEffect } from "react";
import { isRefreshed, REFRESHED_PARAMETER } from "@/domain/session-recovery";

export function ConsumeRefreshMarker() {
  useEffect(() => {
    const location = new URL(window.location.href);
    if (!isRefreshed(location.searchParams)) return;
    location.searchParams.delete(REFRESHED_PARAMETER);
    window.history.replaceState(window.history.state, "", location);
  }, []);
  return <></>;
}
