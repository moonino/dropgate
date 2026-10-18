"use client";

import { useState } from "react";
import type { DropStatus } from "@/api/types";

const SALE_OPENED_MESSAGE = "판매가 열렸습니다";

export function SaleOpenAnnouncement({ status }: { status: DropStatus }) {
  const [initialStatus] = useState(status);
  const opened = initialStatus === "SCHEDULED" && status === "OPEN";
  return (
    <p role="status" className="visually-hidden">
      {opened ? SALE_OPENED_MESSAGE : ""}
    </p>
  );
}
