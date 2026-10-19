#!/usr/bin/env python3
import hashlib
import json
import math
import statistics
import sys
from datetime import datetime
from pathlib import Path

PAIR_COUNT = 5
REQUEST_COUNT = 10000
TOTAL_QUANTITY = 100
TREND_FIELDS = {"p50": "med", "p95": "p(95)", "p99": "p(99)", "max": "max"}
OUTCOMES = ("purchase_confirmed", "purchase_sold_out", "purchase_already_purchased", "purchase_unexpected")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_verified(path, expected_hash):
    content = Path(path).read_bytes()
    require(hashlib.sha256(content).hexdigest() == expected_hash, f"검증 뒤 바뀐 파일입니다: {path}")
    return json.loads(content)


def number(value, name):
    require(type(value) in (int, float) and math.isfinite(value) and value >= 0, f"잘못된 수치입니다: {name}")
    return value


def trend(metrics, metric_name, count, duration):
    metric = metrics[metric_name]
    result = {label: number(metric[key], metric_name + "." + key) for label, key in TREND_FIELDS.items()}
    require(result["p50"] <= result["p95"] <= result["p99"] <= result["max"], "백분위수 순서가 잘못됐습니다")
    return {"requests": count, **result, "rps": count / duration}


def read_run(filename, expected_path):
    manifest = json.loads(Path(filename).read_text())
    require(manifest["schemaVersion"] == 1 and manifest["validated"] is True, "완료 기록이 아닙니다")
    require(manifest["purchasePath"] == expected_path, "db, redis 순서로 다섯 쌍을 입력하세요")
    require((manifest["requestCount"], manifest["totalQuantity"]) == (REQUEST_COUNT, TOTAL_QUANTITY), "부하가 다릅니다")
    summary = read_verified(manifest["summaryFile"], manifest["summarySha256"])
    report = read_verified(manifest["reconciliationFile"], manifest["reconciliationSha256"])
    metrics = summary["metrics"]
    counts = [number(metrics.get(name, {}).get("count", 0), name) for name in OUTCOMES]
    requests = number(metrics["http_reqs"]["count"], "http_reqs.count")
    rate = number(metrics["http_reqs"]["rate"], "http_reqs.rate")
    require(requests == REQUEST_COUNT and sum(counts) == requests, "응답 건수가 요청 건수와 다릅니다")
    require(counts[0] == TOTAL_QUANTITY and counts[3] == 0 and rate > 0, "성공 또는 오류 건수가 잘못됐습니다")
    for metric, threshold in (("purchase_unexpected", "count==0"),
                              ("http_req_duration{status:201}", "max>=0"),
                              ("http_req_duration{status:409}", "max>=0")):
        require(metrics[metric]["thresholds"][threshold] is False, f"임계값이 실패했습니다: {metric}")
    require(report["purchasePath"] == expected_path and report["totalQuantity"] == TOTAL_QUANTITY, "대조 경로가 다릅니다")
    require(report["confirmed"] == TOTAL_QUANTITY and report["canceled"] == 0 and report["duplicatedUsers"] == 0,
            "주문 대조가 실패했습니다")
    require(report["status"] == "SOLD_OUT" and report["outbox"] == {"pending": 0, "published": TOTAL_QUANTITY, "failed": 0}
            and report["streamLength"] == TOTAL_QUANTITY, "발행 또는 판매 상태가 잘못됐습니다")
    if expected_path == "redis":
        require(report["redisStockDifference"] == 0 and report["buyersDifference"] == 0, "Redis 대조 차가 있습니다")
    else:
        require(report["dbStockDifference"] == 0 and report["redisStock"] == TOTAL_QUANTITY and report["buyers"] == 0,
                "DB 대조 또는 Redis 초기 상태가 잘못됐습니다")
    duration = requests / rate
    return {
        "runId": manifest["runId"], "purchasePath": expected_path, "startedAtUtc": manifest["startedAtUtc"],
        "sourceCommit": manifest["sourceCommit"], "jarSha256": manifest["jarSha256"],
        "summaryFile": manifest["summaryFile"], "reconciliationFile": manifest["reconciliationFile"],
        "confirmed": counts[0], "soldOut": counts[1], "alreadyPurchased": counts[2], "unexpected": counts[3],
        "overall": trend(metrics, "http_req_duration", requests, duration),
        "status201": trend(metrics, "http_req_duration{status:201}", counts[0], duration),
        "status409": trend(metrics, "http_req_duration{status:409}", counts[1] + counts[2], duration),
        "reconciliation": report,
    }


def summarize(runs):
    summaries = {}
    for path in ("db", "redis"):
        selected = [run for run in runs if run["purchasePath"] == path]
        summaries[path] = {}
        for scope in ("overall", "status201", "status409"):
            summaries[path][scope] = {}
            for metric in (*TREND_FIELDS, "rps"):
                values = [run[scope][metric] for run in selected]
                summaries[path][scope][metric] = {
                    "min": min(values), "median": statistics.median(values), "max": max(values),
                }
    return summaries


def main(filenames):
    require(len(filenames) == PAIR_COUNT * 2, "사용법: summarize-purchase-runs.py <검증완료 db.json> <redis.json> 순서로 다섯 쌍")
    runs = [read_run(filename, ("db", "redis")[index % 2]) for index, filename in enumerate(filenames)]
    require(len({run["runId"] for run in runs}) == len(runs), "같은 실행이 중복됐습니다")
    require(len({(run["sourceCommit"], run["jarSha256"]) for run in runs}) == 1, "소스 또는 실행 jar가 다릅니다")
    times = [datetime.fromisoformat(run["startedAtUtc"].replace("Z", "+00:00")) for run in runs]
    require(all(first < second for first, second in zip(times, times[1:])), "실제 시작 시각 순서가 아닙니다")
    pairs = []
    for index in range(PAIR_COUNT):
        db, redis = runs[index * 2:index * 2 + 2]
        require(db["overall"]["p99"] > 0, "DB p99가 0입니다")
        pairs.append({
            "pair": index + 1, "dbRunId": db["runId"], "redisRunId": redis["runId"],
            "redisP99ChangePercent": (redis["overall"]["p99"] / db["overall"]["p99"] - 1) * 100,
            "redisRpsChangePercent": (redis["overall"]["rps"] / db["overall"]["rps"] - 1) * 100,
        })
    print(json.dumps({
        "statisticsMeaning": "각 실행 백분위수와 RPS의 중앙값 및 범위이며 전체 요청을 합친 백분위수가 아닙니다",
        "responseRpsMeaning": "해당 응답 건수를 k6 전체 측정 시간으로 나눈 값입니다",
        "runs": runs, "repeatedRunStatistics": summarize(runs), "pairedComparisons": pairs,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except (KeyError, ValueError, OSError) as error:
        print(f"집계 실패: {error}", file=sys.stderr)
        sys.exit(1)
