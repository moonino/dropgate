const KST_TIME_ZONE = "Asia/Seoul";
const KST_OFFSET = "+09:00";
const MILLIS_PER_SECOND = 1000;
const SECONDS_PER_MINUTE = 60;
const SECONDS_PER_HOUR = 3600;

const timeFormat = new Intl.DateTimeFormat("ko-KR", { timeZone: KST_TIME_ZONE, hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
const dayFormat = new Intl.DateTimeFormat("ko-KR", { timeZone: KST_TIME_ZONE, month: "long", day: "numeric", weekday: "short" });
const stampFormat = new Intl.DateTimeFormat("ko-KR", {
  timeZone: KST_TIME_ZONE,
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

export function formatKstTime(iso: string): string {
  return timeFormat.format(new Date(iso));
}

export function formatKstDay(iso: string): string {
  const parts = dayFormat.formatToParts(new Date(iso));
  const read = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value ?? "";
  return `${read("month")} ${read("day")}일 (${read("weekday")})`;
}

export function formatKstStamp(iso: string): string {
  const parts = stampFormat.formatToParts(new Date(iso));
  const read = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value ?? "";
  return `${read("month")}.${read("day")} ${read("hour")}:${read("minute")}:${read("second")}`;
}

export function convertKstInputToUtcIso(localValue: string): string {
  const date = new Date(`${localValue}:00${KST_OFFSET}`);
  if (Number.isNaN(date.getTime())) throw new RangeError(`시각 형식이 아닙니다: ${localValue}`);
  return date.toISOString();
}

export type RemainingTime = { hours: string; minutes: string; seconds: string; finished: boolean };

export function splitRemainingTime(targetIso: string, nowMillis: number): RemainingTime {
  const totalSeconds = Math.max(0, Math.ceil((new Date(targetIso).getTime() - nowMillis) / MILLIS_PER_SECOND));
  const pad = (value: number) => String(value).padStart(2, "0");
  return {
    hours: pad(Math.floor(totalSeconds / SECONDS_PER_HOUR)),
    minutes: pad(Math.floor((totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE)),
    seconds: pad(totalSeconds % SECONDS_PER_MINUTE),
    finished: totalSeconds === 0,
  };
}
