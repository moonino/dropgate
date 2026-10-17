import type { ErrorResponse, FieldError } from "@/api/types";

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
    const retryAfter = Number(response.headers.get("Retry-After"));
    return new BackendError(
      response.status,
      body.code,
      body.message,
      body.errors ?? [],
      Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined,
    );
  }
}

const UNKNOWN_ERROR: ErrorResponse = {
  code: "UNKNOWN",
  message: "잠시 뒤 다시 시도해 주세요.",
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
