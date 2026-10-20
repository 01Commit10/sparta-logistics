#!/usr/bin/env python3
"""P02 주문 생성 부하 실험: 트랜잭션 분리 전/후 × Hikari 풀 크기, 시나리오 A(생성만)·B(생성+조회).

    python3 perf/order-create/run.py seed                      # 업체 2·상품 50(재고 각 1천만)·조회용 주문 1만 건
    python3 perf/order-create/run.py jar before|after [--module delivery-ms]   # 지금 빌드한 jar를 data/에 보관
    python3 perf/order-create/run.py boot --jar before --pool 10 [--delivery after]   # 하위 서비스 + order-ms를 지표 노출 설정으로 다시 띄움
    python3 perf/order-create/run.py probe --rates 40,60,80    # 일정 부하로 풀 고갈 지점 찾기 (보정)
    python3 perf/order-create/run.py run --id 1A --scenario A --peak 120   # 스파이크 측정 1회
    python3 perf/order-create/run.py cleanup

- 실행 중 1초마다 각 서비스 Hikari 지표(관리 포트 2909x)와 pg_stat_activity를 모은다.
- 하위 서비스(hub·delivery·user·company)는 풀 기본값(10) 그대로 두고, order-ms만 jar·풀 크기를 바꾼다.
- Feign 기본 클라이언트(JDK HttpURLConnection)는 대상당 연결 5개만 재사용해 부하 때 연결을 계속 새로 만든다.
  측정 PC의 임시 포트가 바닥나지 않게 모든 서비스를 -Dhttp.maxConnections=200으로 띄운다 (4단계 공통).
- .env 값은 출력하지 않는다.
"""
import argparse
import csv
import gzip
import json
import os
import pathlib
import subprocess
import sys
import threading
import time
import urllib.request
import uuid
from collections import defaultdict

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = ROOT / "perf" / "order-create"
DATA = HERE / "data"
RESULTS = HERE / "results"
sys.path.insert(0, str(ROOT / "e2e" / "order"))
import run as e2e  # noqa: E402  E2E 헬퍼(로그인·업체·상품·재고 생성, SQL, 프로세스 제어) 재사용

e2e.RESULTS = DATA
e2e.STATE = DATA / "created.json"
WORLD = DATA / "world.json"
READ_IDS = DATA / "read-ids.json"
RECORDS = RESULTS / "records.json"

ORDER = "http://localhost:19094"
SERVICES = {"order-ms": 19094, "hub-ms": 19093, "delivery-ms": 19092, "user-ms": 19095, "company-ms": 19091}
MGMT = {name: port + 10000 for name, port in SERVICES.items()}   # 관리 포트를 따로 둬야 Tomcat이 꽉 차도 지표를 읽는다
DB_USERS = {"order_svc": "order-ms", "hub_svc": "hub-ms", "delivery_svc": "delivery-ms", "auth_svc": "user-ms",
            "company_svc": "company-ms"}
HEADERS = {"Content-Type": "application/json", "X-User-Id": e2e.COMP03, "X-User-Role": "USER"}
PRODUCTS = 50
STOCK = 10_000_000
READ_ORDERS = 10_000


# ---------- seed / cleanup ----------

def cmd_seed(_):
    DATA.mkdir(exist_ok=True)
    w = e2e.World()
    products = [w.product(STOCK, name=f"P02 부하 상품 {i:02d}") for i in range(PRODUCTS)]
    world = {"supplier": w.supplier, "requester": w.requester, "orderer": e2e.COMP03, "products": products}
    WORLD.write_text(json.dumps(world, indent=1))
    # 조회용 주문은 API가 아니라 SQL로 바로 만든다 (생성 부하와 섞이지 않게)
    rows = e2e.sql(f"""INSERT INTO orders.p_orders (id, created_at, order_status, ordered_by, product_id, quantity,
  request_note, requester_company_id, supplier_company_id, total_amount, unit_price_snapshot)
SELECT gen_random_uuid(), now(), 'COMPLETED', '{e2e.COMP03}', '{products[0]}', 1, 'P02 조회용',
  '{w.requester}', '{w.supplier}', 5000, 5000 FROM generate_series(1, {READ_ORDERS})
RETURNING id;""")
    READ_IDS.write_text(json.dumps([r[0] for r in rows]))
    print(f"업체 2, 상품 {PRODUCTS}개(재고 각 {STOCK:,}), 조회용 주문 {len(rows):,}건")


def cmd_cleanup(_):
    e2e.cleanup()
    for f in (WORLD, READ_IDS):
        f.unlink(missing_ok=True)


# ---------- 서비스 ----------

def cmd_jar(args):
    jar = module_jar(args.module)
    DATA.mkdir(exist_ok=True)
    target = DATA / f"{args.module}-{args.variant}.jar"
    target.write_bytes(jar.read_bytes())
    print(f"{jar.name} → {target.relative_to(ROOT)}")


def boot(name, jar, extra_env, log):
    env = {**os.environ, "MANAGEMENT_SERVER_PORT": str(MGMT[name]),
           "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE": "health,info,metrics",
           "SERVER_TOMCAT_MBEANREGISTRY_ENABLED": "true", **extra_env}
    subprocess.Popen(["java", "-Xms256m", "-Xmx512m", "-Dhttp.maxConnections=200", "-jar", str(jar)], cwd=ROOT, env=env, stdin=subprocess.DEVNULL,
                     stdout=open(log, "w"), stderr=subprocess.STDOUT, start_new_session=True)


def module_jar(name):
    return [j for j in (ROOT / name / "build" / "libs").glob(f"{name}-*.jar") if not j.name.endswith("-plain.jar")][0]


def metric(name, metric_name, tag=None):
    url = f"http://localhost:{MGMT[name]}/actuator/metrics/{metric_name}" + (f"?tag={tag}" if tag else "")
    with urllib.request.urlopen(url, timeout=2) as res:
        return {m["statistic"]: m["value"] for m in json.loads(res.read())["measurements"]}


def wait_metrics(name, timeout=120):
    for _ in range(timeout):
        try:
            metric(name, "hikaricp.connections.max")
            return
        except Exception:
            time.sleep(1)
    sys.exit(f"{name} 지표를 읽지 못했습니다")


def order_once():
    w = json.loads(WORLD.read_text())
    body = {"requesterCompanyId": w["requester"], "supplierCompanyId": w["supplier"], "productId": w["products"][0],
            "quantity": 1, "unitPrice": 5000, "requestNote": "P02 준비"}
    req = urllib.request.Request(ORDER + "/api/v1/orders", method="POST", data=json.dumps(body).encode(),
                                 headers={**HEADERS, "Idempotency-Key": str(uuid.uuid4())})
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            return res.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception:
        return 0


def cmd_boot(args):
    RESULTS.mkdir(exist_ok=True)
    order_jar = DATA / f"order-ms-{args.jar}.jar"
    if not order_jar.exists():
        sys.exit(f"{order_jar} 없음: 빌드 후 run.py jar {args.jar}")
    targets = ["order-ms"] if args.order_only else list(SERVICES)
    if not args.order_only and not (DATA / f"delivery-ms-{args.delivery}.jar").exists():
        sys.exit(f"delivery-ms-{args.delivery}.jar 없음: 빌드 후 run.py jar {args.delivery} --module delivery-ms")
    for name in targets:
        e2e.shutdown(name)
    for name in targets:
        if name == "order-ms":
            boot(name, order_jar, {"SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE": str(args.pool)}, DATA / f"{name}.log")
        elif name == "delivery-ms":
            boot(name, DATA / f"delivery-ms-{args.delivery}.jar", {}, DATA / f"{name}.log")
        else:
            boot(name, module_jar(name), {}, DATA / f"{name}.log")
    for name in targets:
        wait_metrics(name)
    # Eureka·LoadBalancer 캐시가 새 인스턴스를 볼 때까지 주문이 성공하는지 확인
    for i in range(90):
        if order_once() == 201:
            break
        time.sleep(2)
    else:
        sys.exit("주문이 성공하지 않습니다 (data/*.log 확인)")
    pool = metric("order-ms", "hikaricp.connections.max")["VALUE"]
    print(f"준비 완료: order-ms jar={args.jar}, Hikari max={pool:.0f}" +
          ("" if args.order_only else f", delivery-ms jar={args.delivery}"))


# ---------- 측정 ----------

class Sampler(threading.Thread):
    """1초마다 서비스별 Hikari·Tomcat 지표와 DB 연결 수를 모은다."""

    FIELDS = ["t", "service", "active", "idle", "pending", "max", "timeouts", "usage_ms", "acquire_ms",
              "acquire_max_ms", "tomcat_busy", "db_active", "db_total"]

    def __init__(self, path):
        super().__init__(daemon=True)
        self.path, self.stop = path, threading.Event()
        self.prev = {}

    def sample(self, name):
        g = lambda m: metric(name, m)
        usage, acquire = g("hikaricp.connections.usage"), g("hikaricp.connections.acquire")
        prev = self.prev.get(name)
        self.prev[name] = (usage, acquire)

        def avg_ms(cur, old):
            if not old or cur["COUNT"] == old["COUNT"]:
                return ""
            return round((cur["TOTAL_TIME"] - old["TOTAL_TIME"]) / (cur["COUNT"] - old["COUNT"]) * 1000, 2)

        try:
            busy = metric(name, "tomcat.threads.busy")["VALUE"]
        except Exception:
            busy = ""
        return {"active": g("hikaricp.connections.active")["VALUE"], "idle": g("hikaricp.connections.idle")["VALUE"],
                "pending": g("hikaricp.connections.pending")["VALUE"], "max": g("hikaricp.connections.max")["VALUE"],
                "timeouts": g("hikaricp.connections.timeout")["COUNT"],
                "usage_ms": avg_ms(usage, prev and prev[0]), "acquire_ms": avg_ms(acquire, prev and prev[1]),
                "acquire_max_ms": round(acquire["MAX"] * 1000, 1), "tomcat_busy": busy}

    def db(self):
        rows = e2e.sql("SELECT usename, count(*) FILTER (WHERE state <> 'idle'), count(*) FROM pg_stat_activity "
                       "WHERE usename LIKE '%\\_svc' GROUP BY 1;")
        return {DB_USERS.get(u): (int(a), int(t)) for u, a, t in rows}

    def run(self):
        start = time.time()
        with open(self.path, "w", newline="") as f:
            out = csv.DictWriter(f, self.FIELDS)
            out.writeheader()
            while not self.stop.is_set():
                tick = time.time()
                try:
                    db = self.db()
                except Exception:
                    db = {}
                for name in SERVICES:
                    try:
                        row = self.sample(name)
                    except Exception:
                        continue
                    a, t = db.get(name, ("", ""))
                    out.writerow({"t": round(tick - start, 1), "service": name, **row, "db_active": a, "db_total": t})
                f.flush()
                self.stop.wait(max(0.0, 1 - (time.time() - tick)))


def k6(env, summary, csv_out=None):
    cmd = ["k6", "run", "--quiet", "--no-color", "-e", f"SUMMARY={summary}"]
    for k, v in env.items():
        cmd += ["-e", f"{k}={v}"]
    if csv_out:
        cmd += ["--out", f"csv={csv_out}"]
    cmd.append(str(HERE / "k6" / "order-create.js"))
    subprocess.run(cmd, cwd=HERE / "k6", check=False, stdout=subprocess.DEVNULL, stderr=open(DATA / "k6-stderr.log", "a"))


def measure(run_id, k6_env):
    RESULTS.mkdir(exist_ok=True)
    pool_csv = RESULTS / f"{run_id}-pool.csv"
    summary = RESULTS / f"{run_id}-k6.json"
    raw = DATA / f"{run_id}-k6.csv.gz"
    sampler = Sampler(pool_csv)
    sampler.start()
    started = time.time()
    k6(k6_env, summary, raw)
    sampler.stop.set()
    sampler.join()
    timeseries(raw, RESULTS / f"{run_id}-timeseries.csv", started)
    return summarize(run_id, summary, pool_csv)


def timeseries(raw, out, started):
    """k6 원본(요청 단위)을 1초 단위로 묶는다: 시나리오별 요청 수·실패 수·p95·p99."""
    buckets = defaultdict(list)
    fails = defaultdict(int)
    with gzip.open(raw, "rt") as f:
        for row in csv.DictReader(f):
            if row["metric_name"] != "http_req_duration":
                continue
            key = (int(float(row["timestamp"]) - started), row["scenario"])
            buckets[key].append(float(row["metric_value"]))
            if row["expected_response"] != "true":
                fails[key] += 1
    with open(out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["t", "scenario", "reqs", "fails", "p50_ms", "p95_ms", "p99_ms", "max_ms"])
        for (t, sc), vals in sorted(buckets.items()):
            vals.sort()
            pick = lambda p: round(vals[min(len(vals) - 1, int(len(vals) * p))], 1)
            w.writerow([t, sc, len(vals), fails[(t, sc)], pick(0.5), pick(0.95), pick(0.99), round(vals[-1], 1)])


def summarize(run_id, summary, pool_csv):
    s = json.loads(summary.read_text())["metrics"]

    def trend(name):
        v = s.get(name, {}).get("values", {})
        return {k: round(v.get(k, 0), 1) for k in ("avg", "med", "p(95)", "p(99)", "max")}

    def count(name):
        return int(s.get(name, {}).get("values", {}).get("count", 0))

    rec = {"create": {"latency": trend("http_req_duration{scenario:create}"),
                      "fail_rate": round(s.get("http_req_failed{scenario:create}", {}).get("values", {}).get("rate", 0), 4),
                      "status": {c: count(f"create_{c}") for c in ("201", "409", "500", "503", "other", "0")},
                      "dropped": count("dropped_iterations")}}
    rec["create"]["total"] = sum(rec["create"]["status"].values())
    if "http_req_duration{scenario:read}" in s:
        rec["read"] = {"latency": trend("http_req_duration{scenario:read}"),
                       "fail_rate": round(s["http_req_failed{scenario:read}"]["values"]["rate"], 4),
                       "ok": count("read_200"), "fail": count("read_fail")}
    pools = defaultdict(lambda: {"max_active": 0, "max_pending": 0, "timeouts": [], "usage": [], "acquire_max": 0,
                                 "tomcat_max": 0, "db_max": 0, "exhausted_s": 0})
    with open(pool_csv) as f:
        for row in csv.DictReader(f):
            p = pools[row["service"]]
            num = lambda k: float(row[k]) if row[k] not in ("", None) else None
            p["max_active"] = max(p["max_active"], num("active") or 0)
            p["max_pending"] = max(p["max_pending"], num("pending") or 0)
            p["timeouts"].append(num("timeouts") or 0)
            if num("usage_ms") is not None:
                p["usage"].append(num("usage_ms"))
            p["acquire_max"] = max(p["acquire_max"], num("acquire_max_ms") or 0)
            p["tomcat_max"] = max(p["tomcat_max"], num("tomcat_busy") or 0)
            p["db_max"] = max(p["db_max"], num("db_total") or 0)
            if (num("pending") or 0) > 0 and (num("active") or 0) >= (num("max") or 1):
                p["exhausted_s"] += 1
    rec["pool"] = {}
    for name, p in pools.items():
        usage = sorted(p["usage"])
        rec["pool"][name] = {
            "max_active": int(p["max_active"]), "max_pending": int(p["max_pending"]),
            "timeouts": int(max(p["timeouts"]) - min(p["timeouts"])) if p["timeouts"] else 0,
            "usage_ms_median": usage[len(usage) // 2] if usage else None,
            "usage_ms_max": usage[-1] if usage else None,
            "acquire_max_ms": p["acquire_max"], "tomcat_busy_max": int(p["tomcat_max"]),
            "db_conn_max": int(p["db_max"]), "exhausted_seconds": p["exhausted_s"]}
    records = json.loads(RECORDS.read_text()) if RECORDS.exists() else {}
    records[run_id] = rec
    RECORDS.write_text(json.dumps(records, indent=1, ensure_ascii=False))
    return rec


def show(run_id, rec):
    c = rec["create"]
    o = rec["pool"].get("order-ms", {})
    line = (f"{run_id}: 생성 {c['total']:,}건 201={c['status']['201']:,} 실패율 {c['fail_rate']:.2%} "
            f"p95 {c['latency']['p(95)']}ms p99 {c['latency']['p(99)']}ms | order 풀 active≤{o.get('max_active')} "
            f"pending≤{o.get('max_pending')} timeout {o.get('timeouts')} 고갈 {o.get('exhausted_seconds')}s "
            f"점유 중앙 {o.get('usage_ms_median')}ms")
    if "read" in rec:
        r = rec["read"]
        line += f" | 조회 p99 {r['latency']['p(99)']}ms 실패율 {r['fail_rate']:.2%}"
    print(line)
    others = {n: (p["max_pending"], p["timeouts"]) for n, p in rec["pool"].items() if n != "order-ms" and p["max_pending"]}
    if others:
        print(f"   하위 서비스 pending·timeout: {others}")


def cmd_probe(args):
    for rate in [int(r) for r in args.rates.split(",")]:
        rec = measure(f"probe-{args.tag}-{rate}",
                      {"SCENARIO": "A", "MODE": "constant", "PEAK": rate, "DURATION": args.duration})
        show(f"probe {rate}/s", rec)


def cmd_run(args):
    env = {"SCENARIO": args.scenario, "PEAK": args.peak, "READ_RATE": args.read_rate}
    if args.base:
        env["BASE_RATE"] = args.base
    # 측정 전 JIT·커넥션 워밍업 (기록하지 않음)
    k6({"SCENARIO": "A", "MODE": "constant", "PEAK": 10, "DURATION": "15s"}, DATA / "warmup.json")
    show(args.id, measure(args.id, env))


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("seed").set_defaults(fn=cmd_seed)
    sub.add_parser("cleanup").set_defaults(fn=cmd_cleanup)
    j = sub.add_parser("jar")
    j.add_argument("variant", choices=["before", "after"])
    j.add_argument("--module", choices=["order-ms", "delivery-ms"], default="order-ms")
    j.set_defaults(fn=cmd_jar)
    b = sub.add_parser("boot")
    b.add_argument("--jar", choices=["before", "after"], required=True)
    b.add_argument("--pool", type=int, default=10)
    b.add_argument("--delivery", choices=["before", "after"], default="before",
                   help="delivery-ms 배송 생성 트랜잭션 분리 전/후 (5·6단계는 after)")
    b.add_argument("--order-only", action="store_true")
    b.set_defaults(fn=cmd_boot)
    pr = sub.add_parser("probe")
    pr.add_argument("--rates", required=True)
    pr.add_argument("--duration", default="40s")
    pr.add_argument("--tag", default="1")
    pr.set_defaults(fn=cmd_probe)
    r = sub.add_parser("run")
    r.add_argument("--id", required=True)
    r.add_argument("--scenario", choices=["A", "B"], required=True)
    r.add_argument("--peak", type=int, required=True)
    r.add_argument("--base", type=int)
    r.add_argument("--read-rate", type=int, default=200)
    r.set_defaults(fn=cmd_run)
    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
