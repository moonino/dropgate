"use server";

import { redirect } from "next/navigation";
import type { Drop, FieldError } from "@/api/types";
import { parseDropForm, type DropFormFields } from "@/domain/drop-form";
import type { SessionRefreshRequired } from "@/domain/session-action";
import { runAuthenticatedAction } from "@/server/authenticated-action";
import { callBackend } from "@/server/backend";

export type DropFormState =
  | { kind: "idle" }
  | SessionRefreshRequired
  | { kind: "rejected"; message: string; fieldErrors: FieldError[]; retryAfterSeconds: number | undefined; values: DropFormFields };

const INVALID_FORM_MESSAGE = "입력값을 확인해 주세요.";

export async function createDropAction(_previous: DropFormState, form: FormData): Promise<DropFormState> {
  const values: DropFormFields = {
    name: String(form.get("name") ?? ""),
    totalQuantity: String(form.get("totalQuantity") ?? ""),
    admissionRatePerSec: String(form.get("admissionRatePerSec") ?? ""),
    openAt: String(form.get("openAt") ?? ""),
    closeAt: String(form.get("closeAt") ?? ""),
  };
  return runAuthenticatedAction<DropFormState>(async (accessToken) => {
    const parsed = parseDropForm(values);
    if (parsed.kind === "invalid") return { kind: "rejected", message: INVALID_FORM_MESSAGE, fieldErrors: parsed.fieldErrors, retryAfterSeconds: undefined, values };
    const drop = await callBackend<Drop>("order", "/api/admin/drops", { method: "POST", body: parsed.request, accessToken });
    redirect(`/drops/${drop.id}`);
  }, (error) => {
    return { kind: "rejected", message: error.message, fieldErrors: error.fieldErrors, retryAfterSeconds: error.retryAfterSeconds, values };
  });
}
