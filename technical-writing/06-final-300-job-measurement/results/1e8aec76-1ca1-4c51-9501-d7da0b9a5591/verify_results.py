#!/usr/bin/env python3
"""Independently reconcile measurement CSVs and evaluate agreed load criteria."""
import argparse
import csv
import json
import math
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path


def instant(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")) if value else None


def milliseconds(start, finish):
    return (instant(finish) - instant(start)) // timedelta(milliseconds=1)


def percentiles(values):
    ordered = sorted(values)
    return {
        f"p{p}Millis": ordered[max(0, math.ceil(len(ordered) * p / 100) - 1)] if ordered else None
        for p in (50, 95, 99)
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result_dir", type=Path)
    parser.add_argument("--expected-count", type=int, default=1500)
    parser.add_argument("--submission-duration-seconds", type=float, default=180)
    parser.add_argument("--interval-ms", type=float, default=120)
    parser.add_argument("--p95-limit-ms", type=int, default=500)
    parser.add_argument("--total-deadline-ms", type=int, default=1200000)
    parser.add_argument("--output-dir", type=Path, help="Override output directory for dry runs")
    args = parser.parse_args()
    result_dir = args.result_dir
    output_dir = args.output_dir or result_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    summary = json.loads((result_dir / "summary.json").read_text())
    with (result_dir / "jobs.csv").open(newline="") as stream:
        rows = list(csv.DictReader(stream))
    with (result_dir / "request-latency.csv").open(newline="") as stream:
        latency_rows = list(csv.DictReader(stream))

    checks = []

    def compare(name, actual, expected):
        checks.append({"name": name, "actual": actual, "expected": expected, "passed": actual == expected})

    accepted = [r for r in rows if r["http_status"] == "202" and r["job_id"]
                and r["monster_id"] and not r["request_failure"]]
    job_ids = {r["job_id"] for r in accepted}
    known = {r["job_id"]: r for r in accepted if r["status"]}
    jobs = list(known.values())
    statuses = Counter(r["status"] for r in jobs)
    attempts = [int(r["attempt_count"]) for r in jobs]
    all_terminal = bool(job_ids) and len(job_ids) == len(jobs) and all(
        r["status"] in {"SUCCEEDED", "FAILED"} for r in jobs)
    completion = milliseconds(summary["startedAt"], max((r["finished_at"] for r in jobs), key=instant)) if (
        all_terminal and all(r["finished_at"] for r in jobs)) else None
    metrics = {
        "requestCount": len(rows),
        "http202Count": sum(r["http_status"] == "202" for r in rows),
        "httpFailureCount": len(rows) - len(accepted),
        "uniqueJobIdCount": len(job_ids),
        "duplicateJobIdCount": len(accepted) - len(job_ids),
        "succeededCount": statuses["SUCCEEDED"],
        "failedCount": statuses["FAILED"],
        "pendingCount": statuses["PENDING"],
        "runningCount": statuses["RUNNING"],
        "unfinishedCount": statuses["PENDING"] + statuses["RUNNING"],
        "missingAcceptedJobCount": len(job_ids) - len(jobs),
        "succeededWithoutImageCount": sum(r["status"] == "SUCCEEDED" and not r["image"].strip() for r in jobs),
        "misconnectedResultCount": sum(r["status"] == "SUCCEEDED" and r["image"] != "image:" + r["prompt"] for r in jobs),
        "retriedJobCount": sum(n >= 2 for n in attempts),
        "additionalAttemptCount": sum(max(n - 1, 0) for n in attempts),
        "allAcceptedJobsTerminal": all_terminal,
        "totalCompletionMillis": completion,
        "observedDurationMillis": milliseconds(summary["startedAt"], summary["finishedAt"]),
    }
    for name, actual in metrics.items():
        compare("summary." + name, actual, summary[name])

    latencies = [int(r["request_latency_ms"]) for r in rows]
    waits = [milliseconds(r["created_at"], r["started_at"]) for r in jobs if r["created_at"] and r["started_at"]]
    processing = [milliseconds(r["started_at"], r["finished_at"]) for r in jobs if r["started_at"] and r["finished_at"]]
    for name, values in (("requestLatency", latencies), ("jobWaitingTime", waits), ("jobProcessingTime", processing)):
        for percentile, actual in percentiles([v for v in values if v >= 0]).items():
            compare("summary." + name + "." + percentile, actual, summary[name][percentile])

    shared_columns = ["request_number", "prompt", "http_status", "job_id", "request_started_at",
                      "request_finished_at", "request_latency_ms", "request_failure"]
    compare("request_latency_csv.matches_jobs_csv", latency_rows,
            [{name: row[name] for name in shared_columns} for row in rows])
    # Avoid embedding 1,500 rows in the report once the comparison has been made.
    checks[-1] = {"name": checks[-1]["name"], "actualRows": len(latency_rows),
                  "expectedRows": len(rows), "passed": checks[-1]["passed"]}
    numbers = [int(r["request_number"]) for r in rows]
    compare("request_numbers.cover_expected_range", sorted(numbers), list(range(1, args.expected_count + 1)))
    checks[-1] = {"name": checks[-1]["name"], "uniqueCount": len(set(numbers)),
                  "expectedCount": args.expected_count, "passed": checks[-1]["passed"]}
    compare("unique_monster_ids", len({r["monster_id"] for r in accepted}), args.expected_count)
    compare("csv.job_wait_ms.recomputed", all(
        r["job_wait_ms"] == str(milliseconds(r["created_at"], r["started_at"]))
        for r in jobs if r["created_at"] and r["started_at"]), True)
    compare("csv.processing_ms.recomputed", all(
        r["processing_ms"] == str(milliseconds(r["started_at"], r["finished_at"]))
        for r in jobs if r["started_at"] and r["finished_at"]), True)
    compare("csv.expected_image.matches_prompt", all(r["expected_image"] == "image:" + r["prompt"] for r in jobs), True)
    compare("csv.result_misconnected.flags", all(
        (r["result_misconnected"] == "true") == (r["status"] == "SUCCEEDED" and r["image"] != "image:" + r["prompt"])
        for r in jobs), True)
    compare("csv.missing_from_db.flags", all(
        (r["missing_from_db"] == "true") == (r in accepted and not r["status"]) for r in rows), True)
    compare("csv.timestamps.ordered", all(
        instant(r["created_at"]) <= instant(r["started_at"]) <= instant(r["finished_at"])
        for r in jobs if r["created_at"] and r["started_at"] and r["finished_at"]), True)

    ordered = sorted(rows, key=lambda r: int(r["request_number"]))
    starts = [instant(r["request_started_at"]) for r in ordered if r["request_started_at"]]
    measurement_start = instant(summary["startedAt"])
    offsets = [(s - measurement_start).total_seconds() * 1000 for s in starts]
    intervals = [(b - a).total_seconds() * 1000 for a, b in zip(starts, starts[1:])]
    # The first actual request is the observable origin; no unrecorded scheduler origin is invented.
    lags = [(s - starts[0]).total_seconds() * 1000 - i * args.interval_ms for i, s in enumerate(starts)] if starts else []
    load_profile = {
        "expectedRequestCount": args.expected_count,
        "targetDurationMillis": args.submission_duration_seconds * 1000,
        "targetIntervalMillis": args.interval_ms,
        "timestampedRequestCount": len(starts),
        "firstStartOffsetMillis": min(offsets) if offsets else None,
        "lastStartOffsetMillis": max(offsets) if offsets else None,
        "firstToLastStartMillis": (max(starts) - min(starts)).total_seconds() * 1000 if starts else None,
        "actualStartIntervalMillis": {**percentiles(intervals), "minMillis": min(intervals) if intervals else None,
                                       "maxMillis": max(intervals) if intervals else None},
        "scheduleDeviationFromFirstActualStartMillis": {
            **percentiles(lags), "minMillis": min(lags) if lags else None, "maxMillis": max(lags) if lags else None},
        "scheduleDeviationNote": "Signed deviation from first actual request plus request index times target interval. Informational; no scheduling tolerance is added to acceptance.",
    }
    within_window = len(starts) == args.expected_count and all(
        0 <= v <= args.submission_duration_seconds * 1000 for v in offsets)
    criteria = {
        "requests_started_within_submission_window": within_window,
        "expected_request_count": len(rows) == args.expected_count,
        "all_requests_accepted_202": len(accepted) == args.expected_count and metrics["httpFailureCount"] == 0,
        "unique_job_ids": metrics["uniqueJobIdCount"] == args.expected_count and metrics["duplicateJobIdCount"] == 0,
        "unique_monster_ids": len({r["monster_id"] for r in accepted}) == args.expected_count,
        "post_p95_within_limit": bool(latencies) and percentiles(latencies)["p95Millis"] <= args.p95_limit_ms,
        "all_jobs_succeeded": metrics["succeededCount"] == args.expected_count,
        "no_failed_unfinished_or_missing": all(metrics[k] == 0 for k in (
            "failedCount", "unfinishedCount", "missingAcceptedJobCount")),
        "no_wrong_or_absent_images": metrics["misconnectedResultCount"] == 0 and metrics["succeededWithoutImageCount"] == 0,
        "all_complete_within_deadline": completion is not None and 0 <= completion <= args.total_deadline_ms,
        "independent_artifact_checks_passed": all(c["passed"] for c in checks),
    }
    checked_at = datetime.now(timezone.utc).isoformat()
    verification = {"runId": summary["runId"], "checkedAt": checked_at,
                    "passed": all(c["passed"] for c in checks), "checkCount": len(checks),
                    "recomputedSummary": metrics, "loadProfile": load_profile, "checks": checks,
                    "scope": "CSV/summary reconciliation only; maximumPendingCount requires progress samples and database state is verified separately."}
    acceptance = {"runId": summary["runId"], "checkedAt": checked_at, "allPassed": all(criteria.values()),
                  "criteria": criteria,
                  "thresholds": {"requestCount": args.expected_count,
                                 "submissionDurationSeconds": args.submission_duration_seconds,
                                 "targetIntervalMillis": args.interval_ms,
                                 "postP95LimitMillis": args.p95_limit_ms,
                                 "completionDeadlineMillis": args.total_deadline_ms},
                  "observed": {"postP95Millis": percentiles(latencies)["p95Millis"],
                               "totalCompletionMillis": completion, "loadProfile": load_profile,
                               "retriedJobCount": metrics["retriedJobCount"]},
                  "retryNote": "Retries are reported separately and are not an additional agreed pass/fail threshold."}
    for filename, data in (("verification.json", verification), ("acceptance.json", acceptance)):
        (output_dir / filename).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"runId": summary["runId"], "verificationPassed": verification["passed"],
                      "acceptancePassed": acceptance["allPassed"], "checkCount": len(checks),
                      "failedChecks": [c["name"] for c in checks if not c["passed"]],
                      "failedCriteria": [k for k, v in criteria.items() if not v],
                      "outputDirectory": str(output_dir)}, ensure_ascii=False))
    return 0 if verification["passed"] and acceptance["allPassed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
