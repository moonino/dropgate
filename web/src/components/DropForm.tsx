"use client";

import Link from "next/link";
import { useActionState } from "react";
import type { DropFormState } from "@/app/admin/drops/new/actions";
import { groupFieldErrors, type DropFormFields } from "@/domain/drop-form";
import { appendRetryAfter } from "@/domain/retry";

type DropFormProps = { action: (previous: DropFormState, form: FormData) => Promise<DropFormState> };

const INITIAL_STATE: DropFormState = { kind: "idle" };
const INITIAL_VALUES: DropFormFields = { name: "", totalQuantity: "100", admissionRatePerSec: "50", openAt: "", closeAt: "" };

export function DropForm({ action }: DropFormProps) {
  const [state, submit, pending] = useActionState(action, INITIAL_STATE);
  const errors = state.kind === "rejected" ? groupFieldErrors(state.fieldErrors) : {};
  const values = state.kind === "rejected" ? state.values : INITIAL_VALUES;
  return (
    <form action={submit} noValidate className="panel" style={{ flex: "999 1 560px", minWidth: 0, display: "flex", flexDirection: "column", gap: 24 }}>
      {state.kind === "rejected" && (
        <p role="alert" className="notice notice--danger">
          {appendRetryAfter(state.message, state.retryAfterSeconds)}
        </p>
      )}
      <Field name="name" label="이름" type="text" defaultValue={values.name} hint="100자 이하" error={errors.name} />
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 20 }}>
        <Field name="totalQuantity" label="총수량" type="number" defaultValue={values.totalQuantity} error={errors.totalQuantity} />
        <Field name="admissionRatePerSec" label="초당 입장 인원" type="number" defaultValue={values.admissionRatePerSec} hint="1에서 10000, 기본 50" error={errors.admissionRatePerSec} />
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 20 }}>
        <Field name="openAt" label="오픈 시각" type="datetime-local" defaultValue={values.openAt} error={errors.openAt} />
        <Field name="closeAt" label="마감 시각" type="datetime-local" defaultValue={values.closeAt} hint="오픈 시각보다 늦게" error={errors.closeAt} />
      </div>
      <p className="muted" style={{ margin: 0, fontSize: 13 }}>
        시각은 한국 시간으로 입력하고, 서버에는 UTC로 보냅니다.
      </p>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 12, justifyContent: "flex-end" }}>
        <Link href="/drops" className="button">
          취소
        </Link>
        <button type="submit" className="button button--primary" disabled={pending}>
          {pending ? "등록 중" : "드롭 등록"}
        </button>
      </div>
    </form>
  );
}

type FieldProps = { name: string; label: string; type: string; defaultValue: string; hint?: string; error: string | undefined };

function Field({ name, label, type, defaultValue, hint, error }: FieldProps) {
  const messageId = `${name}-message`;
  const message = error ?? hint;
  return (
    <div className="field">
      <label htmlFor={name} className="field__label">
        {label}
      </label>
      <input
        id={name}
        name={name}
        type={type}
        defaultValue={defaultValue}
        className={type === "text" ? "field__input" : "field__input mono"}
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? messageId : undefined}
      />
      {message && (
        <span id={messageId} className={error ? "field__message field__message--error" : "field__message"}>
          {message}
        </span>
      )}
    </div>
  );
}
