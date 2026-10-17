"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import type { Drop, FieldError } from "@/api/types";
import { parseDropForm } from "@/domain/drop-form";
import { callBackend } from "@/server/backend";
import { BackendError } from "@/server/backend-error";
import { readSession } from "@/server/session";

export type DropFormState = { kind: "idle" } | { kind: "rejected"; message: string; fieldErrors: FieldError[] };

const INVALID_FORM_MESSAGE = "입력값을 확인해 주세요.";

export async function createDropAction(_previous: DropFormState, form: FormData): Promise<DropFormState> {
  const session = readSession(await cookies());
  if (session.kind === "anonymous") redirect("/login");
  const parsed = parseDropForm({
    name: String(form.get("name") ?? ""),
    totalQuantity: String(form.get("totalQuantity") ?? ""),
    admissionRatePerSec: String(form.get("admissionRatePerSec") ?? ""),
    openAt: String(form.get("openAt") ?? ""),
    closeAt: String(form.get("closeAt") ?? ""),
  });
  if (parsed.kind === "invalid") return { kind: "rejected", message: INVALID_FORM_MESSAGE, fieldErrors: parsed.fieldErrors };
  let drop: Drop;
  try {
    drop = await callBackend<Drop>("order", "/api/admin/drops", { method: "POST", body: parsed.request, accessToken: session.accessToken });
  } catch (error) {
    if (!(error instanceof BackendError)) throw error;
    if (error.status === 401) redirect("/login");
    return { kind: "rejected", message: error.message, fieldErrors: error.fieldErrors };
  }
  redirect(`/drops/${drop.id}`);
}
