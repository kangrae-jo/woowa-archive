#!/usr/bin/env python3
"""04 Job Queue 구현에서 Worker를 kill -9로 종료했다가 재시작해 복구되는지 확인한다.

임시 MySQL 인스턴스를 만들어 사용하고 끝나면 지운다. 기존 DB는 건드리지 않는다.
외부 라이브러리 없이 표준 라이브러리와 mysql CLI만 사용한다.

실행 전 Gradle runtime classpath를 파일로 저장해 --classpath-file로 넘긴다. README 참고.
"""
import argparse
import csv
import datetime
import json
import os
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

KST = datetime.timezone(datetime.timedelta(hours=9))
DB_NAME = "technical_writing_kill_recovery"
WEB_MAIN = "com.kng0501.dbqueue.server.WebServerApplication"
WORKER_MAIN = "com.kng0501.dbqueue.worker.ImageWorkerApplication"
COLUMNS = [
    "job_id", "monster_id", "prompt", "status", "attempt_count", "next_attempt_at",
    "deadline_at", "claim_token", "created_at", "started_at", "finished_at", "last_error", "image",
]


def log(message):
    print(f"[{datetime.datetime.now(KST).strftime('%H:%M:%S.%f')[:-3]}] {message}", flush=True)


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class Database:
    def __init__(self, mysql_port):
        self.base = ["mysql", "--no-defaults", "-h127.0.0.1", f"-P{mysql_port}", "-uroot", "-N", "-B", "--raw"]

    def run(self, sql, database=DB_NAME):
        command = self.base + ([database] if database else []) + ["-e", sql]
        done = subprocess.run(command, capture_output=True, text=True)
        if done.returncode != 0:
            raise RuntimeError(done.stderr.strip())
        return done.stdout

    def snapshot(self):
        sql = (
            "select j.job_id, j.monster_id, j.prompt, j.status, j.attempt_count, j.next_attempt_at, "
            "ifnull(j.deadline_at,''), ifnull(j.claim_token,''), j.created_at, ifnull(j.started_at,''), "
            "ifnull(j.finished_at,''), replace(replace(ifnull(j.last_error,''),'\\n',' '),'\\t',' '), "
            "ifnull(m.image,'') from image_generation_job j join queue_monster m on m.monster_id=j.monster_id "
            "order by j.job_id"
        )
        rows = []
        for line in self.run(sql).splitlines():
            rows.append(dict(zip(COLUMNS, line.split("\t"))))
        return rows

    def counts(self):
        result = {"PENDING": 0, "RUNNING": 0, "SUCCEEDED": 0, "FAILED": 0}
        for line in self.run("select status, count(*) from image_generation_job group by status").splitlines():
            status, count = line.split("\t")
            result[status] = int(count)
        return result


def write_csv(path, rows):
    with open(path, "w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)


def http(method, url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        return response.status, json.loads(response.read() or b"null")


def wait_until(predicate, timeout, description, interval=0.2):
    started = time.monotonic()
    while time.monotonic() - started < timeout:
        value = predicate()
        if value:
            return value
        time.sleep(interval)
    raise TimeoutError(f"시간 초과: {description}")


def start_java(args, main_class, program_args, env, log_path):
    handle = open(log_path, "w", encoding="utf-8")
    command = [args.java, "-Xms128m", "-Xmx512m", "-cp", args.classpath, main_class, *program_args]
    return subprocess.Popen(command, env=env, stdout=handle, stderr=subprocess.STDOUT)


def worker_args(args):
    return [f"--db-queue.concurrency={args.concurrency}", f"--image-generator.delay={args.delay_seconds}s"]


def wait_worker_ready(log_path, timeout=90):
    wait_until(lambda: "Started ImageWorkerApplication" in Path(log_path).read_text(encoding="utf-8"),
               timeout, "Worker 기동")


def single_run(index, args, db, web_url, env, out_dir):
    run_dir = out_dir / f"run-{index}"
    run_dir.mkdir(parents=True)
    db.run("delete from image_generation_job; delete from queue_monster;")
    log(f"run {index}: Worker 시작")
    worker_log = run_dir / "worker-before-kill.log"
    worker = start_java(args, WORKER_MAIN, worker_args(args), env, worker_log)
    second_worker = None
    try:
        wait_worker_ready(worker_log)

        accepted = {}
        for number in range(args.jobs):
            prompt = f"kill-recovery-{index}-{number}"
            status, body = http("POST", f"{web_url}/jobs", {"prompt": prompt})
            assert status == 202, status
            accepted[body["jobId"]] = prompt
        log(f"run {index}: {len(accepted)}건 접수")

        def ready_to_kill():
            counts = db.counts()
            return counts if counts["SUCCEEDED"] >= args.concurrency and counts["RUNNING"] >= args.concurrency else None

        wait_until(ready_to_kill, 120, "종료 시점 도달")
        time.sleep(args.kill_delay_seconds)

        killed_at = time.monotonic()
        killed_at_kst = datetime.datetime.now(KST).isoformat()
        os.kill(worker.pid, signal.SIGKILL)
        worker_exit = worker.wait(timeout=10)
        time.sleep(0.3)
        at_kill = db.snapshot()
        write_csv(run_dir / "at-kill.csv", at_kill)
        orphans = {row["job_id"]: row for row in at_kill if row["status"] == "RUNNING"}
        counts_at_kill = db.counts()
        log(f"run {index}: kill -9 완료, exit={worker_exit}, 상태={counts_at_kill}")

        status, extra = http("POST", f"{web_url}/jobs", {"prompt": f"kill-recovery-{index}-during-outage"})
        accepted_during_outage = status == 202
        accepted[extra["jobId"]] = f"kill-recovery-{index}-during-outage"

        wait_until(
            lambda: db.run(
                "select count(*) from image_generation_job where status='RUNNING' "
                f"and date_add(deadline_at, interval {args.grace_seconds} second) >= utc_timestamp(6)"
            ).strip() == "0",
            args.timeout_seconds + args.grace_seconds + 30,
            "모든 RUNNING의 처리 기한 경과",
        )
        during_outage = db.snapshot()
        write_csv(run_dir / "during-outage.csv", during_outage)
        before = {row["job_id"]: row for row in at_kill}
        unchanged = all(
            before[row["job_id"]] == row for row in during_outage if row["job_id"] in before
        )
        sample_id = next(iter(orphans))
        _, sample = http("GET", f"{web_url}/jobs/{sample_id}")
        deadline = datetime.datetime.fromisoformat(sample["deadlineAt"].replace("Z", "+00:00"))
        sample_expired_visible = sample["status"] == "RUNNING" and deadline < datetime.datetime.now(datetime.timezone.utc)
        log(f"run {index}: 기한 경과 후에도 DB 변화 없음={unchanged}, 조회={sample['status']}")

        restart_log = run_dir / "worker-after-restart.log"
        restarted_at = time.monotonic()
        second_worker = start_java(args, WORKER_MAIN, worker_args(args), env, restart_log)
        wait_worker_ready(restart_log)
        worker_ready_seconds = time.monotonic() - restarted_at

        orphan_tokens = {job_id: row["claim_token"] for job_id, row in orphans.items()}
        recovered_after = {}

        def all_terminal():
            rows = db.snapshot()
            now = time.monotonic() - restarted_at
            by_id = {row["job_id"]: row for row in rows}
            for job_id, token in orphan_tokens.items():
                if job_id not in recovered_after and by_id[job_id]["claim_token"] != token:
                    recovered_after[job_id] = now
            return rows if all(row["status"] in ("SUCCEEDED", "FAILED") for row in rows) else None

        final = wait_until(all_terminal, args.finish_timeout_seconds, "모든 Job 종결", interval=0.1)
        finished_after = time.monotonic() - restarted_at
        write_csv(run_dir / "final.csv", final)

        final_by_id = {row["job_id"]: row for row in final}
        orphan_rows = [final_by_id[job_id] for job_id in orphans]
        other_rows = [row for row in final if row["job_id"] not in orphans]
        checks = {
            "worker_killed_by_sigkill": worker_exit == -signal.SIGKILL,
            "app_server_accepted_during_outage": accepted_during_outage,
            "no_db_change_after_deadline_while_worker_down": unchanged,
            "expired_running_visible_via_get": sample_expired_visible,
            "all_jobs_succeeded": all(row["status"] == "SUCCEEDED" for row in final),
            "no_job_lost": {str(job_id) for job_id in accepted} == set(final_by_id),
            "orphans_reclaimed_once": all(row["attempt_count"] == "2" for row in orphan_rows),
            "other_jobs_single_attempt": all(row["attempt_count"] == "1" for row in other_rows),
            "images_match_prompt": all(row["image"] == f"image:{row['prompt']}" for row in final),
            "no_failed_jobs": all(row["status"] != "FAILED" for row in final),
        }
        result = {
            "run": index,
            "passed": all(checks.values()),
            "checks": checks,
            "killedAtKst": killed_at_kst,
            "workerExitCode": worker_exit,
            "countsAtKill": counts_at_kill,
            "orphanRunningJobs": len(orphans),
            "totalJobs": len(final),
            "workerDownSeconds": round(restarted_at - killed_at, 3),
            "workerReadySecondsAfterRestart": round(worker_ready_seconds, 3),
            "firstOrphanReclaimedSecondsAfterRestart": round(min(recovered_after.values()), 3),
            "lastOrphanReclaimedSecondsAfterRestart": round(max(recovered_after.values()), 3),
            "allJobsFinishedSecondsAfterRestart": round(finished_after, 3),
            "sampleLastError": orphan_rows[0]["last_error"],
        }
        log(f"run {index}: passed={result['passed']}")
        return result
    finally:
        for process in (worker, second_worker):
            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--classpath-file", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--work-dir", required=True, help="임시 MySQL datadir를 만들 위치")
    parser.add_argument("--output-root", default=str(Path(__file__).parent / "results"))
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--jobs", type=int, default=40)
    parser.add_argument("--concurrency", type=int, default=8)
    parser.add_argument("--delay-seconds", type=int, default=5)
    parser.add_argument("--timeout-seconds", type=int, default=30)
    parser.add_argument("--grace-seconds", type=int, default=3)
    parser.add_argument("--kill-delay-seconds", type=float, default=1.0)
    parser.add_argument("--finish-timeout-seconds", type=int, default=180)
    args = parser.parse_args()
    args.classpath = Path(args.classpath_file).read_text(encoding="utf-8").strip()

    root = Path(__file__).resolve().parent.parent
    stamp = datetime.datetime.now(KST).strftime("%Y%m%d-%H%M%S")
    out_dir = Path(args.output_root) / stamp
    out_dir.mkdir(parents=True)
    work = Path(args.work_dir) / f"mysql-{stamp}"
    work.mkdir(parents=True)
    data_dir = work / "data"
    mysql_port = free_port()
    web_port = free_port()
    mysql = web = None
    started_kst = datetime.datetime.now(KST).isoformat()
    try:
        subprocess.run(["mysqld", "--no-defaults", "--initialize-insecure", f"--datadir={data_dir}",
                        f"--log-error={work / 'init.log'}"], check=True)
        mysql = subprocess.Popen(
            ["mysqld", "--no-defaults", f"--datadir={data_dir}", "--socket=m.sock", "--pid-file=m.pid",
             f"--port={mysql_port}", "--bind-address=127.0.0.1", "--mysqlx=OFF", "--skip-log-bin",
             "--default-time-zone=+00:00", "--innodb-buffer-pool-size=67108864",
             f"--log-error={work / 'server.log'}"],
            cwd=work,
        )
        db = Database(mysql_port)

        def mysql_ready():
            try:
                db.run("select 1", database=None)
                return True
            except RuntimeError:
                return False

        wait_until(mysql_ready, 60, "MySQL 기동", interval=0.5)
        db.run(f"create database {DB_NAME}", database=None)
        schema = root / "04-reliability-hardening/src/main/resources/db/04/schema.sql"
        db.run(schema.read_text(encoding="utf-8"))

        env = {
            **os.environ,
            "TECHNICAL_WRITING_DB_URL": (
                f"jdbc:mysql://127.0.0.1:{mysql_port}/{DB_NAME}?connectionTimeZone=UTC"
                "&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true"
            ),
            "TECHNICAL_WRITING_DB_USERNAME": "root",
            "TECHNICAL_WRITING_DB_PASSWORD": "",
        }
        web = start_java(args, WEB_MAIN, [f"--server.port={web_port}"], env, out_dir / "web.log")

        def web_ready():
            try:
                with socket.create_connection(("127.0.0.1", web_port), timeout=1):
                    return True
            except OSError:
                return False

        wait_until(web_ready, 90, "앱 서버 기동", interval=0.5)
        web_url = f"http://127.0.0.1:{web_port}"

        results = [single_run(index, args, db, web_url, env, out_dir) for index in range(1, args.runs + 1)]

        versions = {
            "java": subprocess.run([args.java, "-version"], capture_output=True, text=True).stderr.splitlines()[0],
            "mysql": db.run("select version()", database=None).strip(),
            "isolation": db.run("select @@transaction_isolation", database=None).strip(),
            "os": subprocess.run(["uname", "-srm"], capture_output=True, text=True).stdout.strip(),
            "cpu": subprocess.run(["sysctl", "-n", "machdep.cpu.brand_string"], capture_output=True, text=True).stdout.strip(),
        }
        git_head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True).stdout.strip()
        summary = {
            "startedAtKst": started_kst,
            "finishedAtKst": datetime.datetime.now(KST).isoformat(),
            "gitHead": git_head,
            "environment": versions,
            "conditions": {
                "runs": args.runs,
                "jobsPerRun": args.jobs,
                "extraJobDuringOutage": 1,
                "workerProcesses": 1,
                "workerConcurrency": args.concurrency,
                "generatorDelaySeconds": args.delay_seconds,
                "processingTimeoutSeconds": args.timeout_seconds,
                "retryDelaySeconds": 1,
                "maxAttempts": 3,
                "pollingAndRecoveryIntervalMs": 100,
                "killSignal": "SIGKILL",
                "killTiming": f"SUCCEEDED와 RUNNING이 각각 {args.concurrency}건 이상이 된 {args.kill_delay_seconds}초 뒤",
                "restartTiming": f"종료 당시 RUNNING의 처리 기한이 {args.grace_seconds}초 이상 지난 뒤",
            },
            "allPassed": all(result["passed"] for result in results),
            "runs": results,
        }
        (out_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
        log(f"완료: {out_dir} allPassed={summary['allPassed']}")
        return 0 if summary["allPassed"] else 1
    finally:
        if web is not None and web.poll() is None:
            web.terminate()
            try:
                web.wait(timeout=15)
            except subprocess.TimeoutExpired:
                web.kill()
        if mysql is not None and mysql.poll() is None:
            mysql.terminate()
            try:
                mysql.wait(timeout=30)
            except subprocess.TimeoutExpired:
                mysql.kill()
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
