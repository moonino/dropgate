"use server";

import type { DropPage } from "@/api/types";
import { callBackendOrUnavailable, type BackendResult } from "@/server/backend";

export async function loadDropsPage(cursor: string): Promise<BackendResult<DropPage>> {
  return callBackendOrUnavailable<DropPage>("order", `/api/drops?cursor=${encodeURIComponent(cursor)}`);
}
