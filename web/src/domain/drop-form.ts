import type { CreateDropRequest, FieldError } from "@/api/types";
import { convertKstInputToUtcIso } from "./time";

export type DropFormFields = {
  name: string;
  totalQuantity: string;
  admissionRatePerSec: string;
  openAt: string;
  closeAt: string;
};

export type ParsedDropForm = { kind: "valid"; request: CreateDropRequest } | { kind: "invalid"; fieldErrors: FieldError[] };

const MISSING_TIME_MESSAGE = "시각을 입력해 주세요";
const NOT_A_NUMBER_MESSAGE = "숫자를 입력해 주세요";

export function parseDropForm(fields: DropFormFields): ParsedDropForm {
  const fieldErrors: FieldError[] = [];
  const readTime = (field: "openAt" | "closeAt") => {
    try {
      return convertKstInputToUtcIso(fields[field]);
    } catch {
      fieldErrors.push({ field, message: MISSING_TIME_MESSAGE });
      return "";
    }
  };
  const readInteger = (field: "totalQuantity" | "admissionRatePerSec") => {
    const value = Number(fields[field]);
    if (fields[field].trim() === "" || !Number.isInteger(value)) fieldErrors.push({ field, message: NOT_A_NUMBER_MESSAGE });
    return value;
  };
  const request: CreateDropRequest = {
    name: fields.name,
    totalQuantity: readInteger("totalQuantity"),
    admissionRatePerSec: readInteger("admissionRatePerSec"),
    openAt: readTime("openAt"),
    closeAt: readTime("closeAt"),
  };
  return fieldErrors.length > 0 ? { kind: "invalid", fieldErrors } : { kind: "valid", request };
}

export function groupFieldErrors(fieldErrors: FieldError[]): Record<string, string> {
  return Object.fromEntries(fieldErrors.map((error) => [error.field, error.message]));
}
