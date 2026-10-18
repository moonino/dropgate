import type { ErrorResponse, FieldError } from "@/api/types";
import { parseRetryAfterSeconds } from "@/domain/retry";

const UNAUTHORIZED = 401;
const SERVICE_UNAVAILABLE = 503;

export class BackendError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: FieldError[],
    readonly retryAfterSeconds: number | undefined,
  ) {
    super(message);
    this.name = "BackendError";
  }

  static async fromResponse(response: Response): Promise<BackendError> {
    const body = await readErrorBody(response);
    return new BackendError(response.status, body.code, body.message, body.errors ?? [], parseRetryAfterSeconds(response.headers.get("Retry-After")));
  }

  static unreachable(): BackendError {
    return new BackendError(SERVICE_UNAVAILABLE, UNREACHABLE_ERROR.code, UNREACHABLE_ERROR.message, [], undefined);
  }

  isUnauthenticated(): boolean {
    return this.status === UNAUTHORIZED;
  }

  isUnavailable(): boolean {
    return this.status === SERVICE_UNAVAILABLE;
  }
}

const UNKNOWN_ERROR: ErrorResponse = {
  code: "UNKNOWN",
  message: "잠시 뒤 다시 시도해 주세요.",
  errors: [],
};

const UNREACHABLE_ERROR: ErrorResponse = {
  code: "UNKNOWN",
  message: "서버에 연결하지 못했습니다.",
  errors: [],
};

async function readErrorBody(response: Response): Promise<ErrorResponse> {
  try {
    const body = (await response.json()) as Partial<ErrorResponse>;
    if (typeof body.code === "string" && typeof body.message === "string") {
      return { code: body.code, message: body.message, errors: body.errors ?? [] };
    }
    return UNKNOWN_ERROR;
  } catch {
    return UNKNOWN_ERROR;
  }
}
