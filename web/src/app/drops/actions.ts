"use server";

import type { DropPage } from "@/api/types";
import { callBackend } from "@/server/backend";

export async function loadDropsPage(cursor: string): Promise<DropPage> {
  return callBackend<DropPage>("order", `/api/drops?cursor=${encodeURIComponent(cursor)}`);
}
