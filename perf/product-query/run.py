#!/usr/bin/env python3
"""P01 상품 조회 최적화 실험: 단계(S1~S5)마다 인덱스·캐시를 바꿔 company-ms를 다시 띄우고 k6로 잰다.

    python3 perf/product-query/run.py seed                 # 실험 데이터 100만 건 (한 번)
    python3 perf/product-query/run.py run                  # S1~S4 × top·detail·search (S5는 --stages로)
    python3 perf/product-query/run.py run --stages S3,S4 --apis top --vus 100
    python3 perf/product-query/run.py run --stages S4,S5 --vus 50,200 --results results/p03   # P03 재측정
    python3 perf/product-query/run.py cleanup              # 실험 데이터·인덱스 지우기

- 저장소 루트에서 실행한다. postgres·redis 컨테이너가 떠 있어야 한다. 다른 서비스(eureka 등)는 필요 없다.
- .env에서 DB·Redis 접속 정보를 읽고 값은 출력하지 않는다.
- 결과: perf/product-query/results/ (k6 요약 JSON, 단계별 실행 계획, DB 통계, summary.md)
"""
import argparse
import json
import os
import pathlib
import signal
import subprocess
import sys
import time
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = ROOT / "perf" / "product-query"
RESULTS = HERE / "results"
DATA = HERE / "data"
PORT = 19091

STAGES = {
    "S1": {"index": "off", "cache": "none", "label": "인덱스 X"},
    "S2": {"index": "on", "cache": "none", "label": "인덱스 O"},
    "S3": {"index": "on", "cache": "redis", "label": "인덱스 O + Redis 캐시"},
    "S4": {"index": "on", "cache": "layered", "label": "인덱스 O + Caffeine·Redis 레이어드 캐시"},
    "S5": {"index": "on", "cache": "hot", "label": "인덱스 O + hot (TOP N L1 스냅샷 · 순위 ZSET · single-flight)"},
}
APIS = {"top": "인기 상품 TOP 10", "detail": "상품 상세 (+조회수)", "search": "이름 검색"}


def load_env():
    env = dict(os.environ)
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            env.setdefault(key.strip(), value.strip().strip('"').strip("'"))
    return env


ENV = load_env()


def psql(sql=None, file=None, variables=None, capture=True):
    cmd = ["docker", "exec", "-i", "-e", f"PGPASSWORD={ENV['DB_PASSWORD']}", "sparta-logistics-postgres",
           "psql", "-v", "ON_ERROR_STOP=1", "-U", ENV["DB_USER"], "-d", ENV["DB_NAME"], "-q", "-A", "-t"]
    for key, value in (variables or {}).items():
        cmd += ["-v", f"{key}={value}"]
    stdin = (HERE / "sql" / file).read_text(encoding="utf-8") if file else sql
    result = subprocess.run(cmd, input=stdin, text=True, capture_output=capture, check=True)
    return result.stdout.strip() if capture else ""


def redis_cli(*args):
    cmd = ["docker", "exec", "-e", f"REDISCLI_AUTH={ENV.get('REDIS_PASSWORD', '')}", "sparta-logistics-redis",
           "redis-cli", "--no-auth-warning", *args]
    return subprocess.run(cmd, text=True, capture_output=True, check=True).stdout


def clear_product_cache():
    # 실험 캐시와 조회수 키만 지운다 (다른 서비스·토큰 키는 그대로)
    for pattern in ("company-ms::product-*", "company-ms::product-views*"):
        keys = redis_cli("--scan", "--pattern", pattern).split()
        for i in range(0, len(keys), 500):
            redis_cli("DEL", *keys[i:i + 500])


def redis_stats():
    info = redis_cli("INFO", "stats")
    stats = dict(line.split(":", 1) for line in info.splitlines() if ":" in line)
    return {"hits": int(stats["keyspace_hits"]), "misses": int(stats["keyspace_misses"])}


def table_stats():
    row = psql("SELECT seq_scan, seq_tup_read, coalesce(idx_scan,0), coalesce(idx_tup_fetch,0), n_tup_upd "
               "FROM pg_stat_user_tables WHERE schemaname='company' AND relname='p_products';")
    keys = ["seq_scan", "seq_tup_read", "idx_scan", "idx_tup_fetch", "n_tup_upd"]
    return dict(zip(keys, map(int, row.split("|"))))


def stop_company():
    out = subprocess.run(["lsof", "-ti", f"tcp:{PORT}", "-sTCP:LISTEN"], text=True, capture_output=True).stdout
    for pid in out.split():
        os.kill(int(pid), signal.SIGTERM)
    for _ in range(60):
        if not subprocess.run(["lsof", "-ti", f"tcp:{PORT}", "-sTCP:LISTEN"], capture_output=True).stdout:
            return
        time.sleep(0.5)


def jar_path():
    jars = sorted((ROOT / "company-ms" / "build" / "libs").glob("company-ms*.jar"))
    jars = [j for j in jars if not j.name.endswith("-plain.jar")]
    if not jars:
        sys.exit("company-ms jar가 없습니다: sh ./gradlew :company-ms:bootJar")
    return jars[-1]


def start_company(mode, log):
    env = dict(os.environ)
    env["PRODUCT_CACHE_MODE"] = mode
    env["EUREKA_CLIENT_ENABLED"] = "false"
    cmd = ["java", "-Xms1g", "-Xmx1g", "-jar", str(jar_path()), "--eureka.client.enabled=false"]
    subprocess.Popen(cmd, cwd=ROOT, env=env, stdin=subprocess.DEVNULL, stdout=open(log, "w"),
                     stderr=subprocess.STDOUT, start_new_session=True)
    url = f"http://localhost:{PORT}/api/v1/products?size=1"
    headers = {"X-User-Id": "9b2f0a3e-5c1d-4e8a-9f00-000000000001", "X-User-Role": "USER"}
    for _ in range(120):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=5) as res:
                if res.status == 200:
                    return
        except Exception:
            time.sleep(1)
    sys.exit(f"company-ms가 뜨지 않았습니다: {log}")


def k6(api, vus, duration, summary=None):
    cmd = ["k6", "run", "--quiet", "-e", f"API={api}", "-e", f"VUS={vus}", "-e", f"DURATION={duration}"]
    if summary:
        cmd += ["--summary-export", str(summary)]
    cmd.append(str(HERE / "k6" / "product-query.js"))
    subprocess.run(cmd, cwd=HERE / "k6", capture_output=True, text=True)


def export_ids():
    DATA.mkdir(exist_ok=True)
    rows = psql("SELECT id FROM company.p_products WHERE company_id BETWEEN "
                "'c0000000-0000-4000-8000-000000000001' AND 'c0000000-0000-4000-8000-000000000100' "
                "AND deleted_at IS NULL ORDER BY view_count DESC LIMIT 10000;").split()
    (DATA / "ids.json").write_text(json.dumps(rows))
    return len(rows)


def cmd_seed(_):
    started = time.time()
    psql(file="seed.sql", capture=False)
    count = psql("SELECT count(*) FROM company.p_products;")
    print(f"상품 {count}건 ({time.time() - started:.0f}초)")
    print(f"상세 조회용 ID {export_ids()}개 → {DATA / 'ids.json'}")


def cmd_cleanup(_):
    stop_company()
    psql(file="cleanup.sql", capture=False)
    clear_product_cache()
    print("실험 데이터·인덱스·캐시 키를 지웠습니다.")


def summarize(path):
    metrics = json.loads(path.read_text())["metrics"]
    duration = metrics["http_req_duration"]
    return {
        "rps": metrics["http_reqs"]["rate"],
        "avg": duration["avg"], "med": duration["med"], "p95": duration["p(95)"], "p99": duration["p(99)"],
        "failed": metrics["http_req_failed"]["value"] if "value" in metrics["http_req_failed"]
        else metrics["http_req_failed"].get("rate", 0),
        "count": metrics["http_reqs"]["count"],
    }


def cmd_run(args):
    global RESULTS
    RESULTS = HERE / args.results
    RESULTS.mkdir(parents=True, exist_ok=True)
    if not (DATA / "ids.json").exists():
        export_ids()
    stages = args.stages.split(",")
    apis = args.apis.split(",")
    records = json.loads((RESULTS / "records.json").read_text()) if (RESULTS / "records.json").exists() else {}
    for stage in stages:
        conf = STAGES[stage]
        print(f"== {stage} {conf['label']}")
        stop_company()
        psql(file=f"index-{conf['index']}.sql", capture=False)
        clear_product_cache()
        (RESULTS / f"{stage}-explain.txt").write_text(psql(file="explain.sql", variables={"keyword": "고등어"}))
        start_company(conf["cache"], RESULTS / f"{stage}-company-ms.log")
        for api, vus in ((api, vus) for api in apis for vus in args.vus):
            name = f"{stage}-{api}" if vus == 50 else f"{stage}-{api}-vu{vus}"   # VU 50이 기본 결과
            k6(api, vus, args.warmup)                          # 워밍업 (JIT·커넥션 풀·캐시 채우기), 결과 버림
            before_db, before_redis = table_stats(), redis_stats()
            summary = RESULTS / f"{name}.json"
            k6(api, vus, args.duration, summary)
            after_db, after_redis = table_stats(), redis_stats()
            result = summarize(summary)
            result["db"] = {k: after_db[k] - before_db[k] for k in after_db}
            result["redis"] = {k: after_redis[k] - before_redis[k] for k in after_redis}
            result["vus"] = vus
            records[name] = result
            print(f"   {api:6} VU {vus:4} {result['rps']:8.0f} req/s  p95 {result['p95']:8.1f} ms  "
                  f"p99 {result['p99']:8.1f} ms  실패 {result['failed'] * 100:.2f}%")
        stop_company()
        if conf["cache"] != "none":
            time.sleep(1)
    (RESULTS / "records.json").write_text(json.dumps(records, ensure_ascii=False, indent=2))
    write_summary(records, args)


def write_summary(records, args):
    lines = [f"# P01 측정 결과 (VU 50, {args.duration}, 워밍업 {args.warmup})", ""]
    for api, title in APIS.items():
        lines += [f"## {title}", "",
                  "| 단계 | req/s | 평균 ms | p50 ms | p95 ms | p99 ms | 실패 | DB seq scan | DB 읽은 행(seq) | DB idx scan | DB UPDATE | Redis hit | Redis miss |",
                  "|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
        for stage, conf in STAGES.items():
            r = records.get(f"{stage}-{api}")
            if not r:
                continue
            db, rd = r["db"], r["redis"]
            lines.append(f"| {stage} {conf['label']} | {r['rps']:.0f} | {r['avg']:.1f} | {r['med']:.1f} | "
                         f"{r['p95']:.1f} | {r['p99']:.1f} | {r['failed'] * 100:.2f}% | {db['seq_scan']:,} | "
                         f"{db['seq_tup_read']:,} | {db['idx_scan']:,} | {db['n_tup_upd']:,} | "
                         f"{rd['hits']:,} | {rd['misses']:,} |")
        lines.append("")
    (RESULTS / "summary.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"→ {RESULTS / 'summary.md'}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("seed").set_defaults(func=cmd_seed)
    sub.add_parser("cleanup").set_defaults(func=cmd_cleanup)
    run = sub.add_parser("run")
    run.add_argument("--stages", default="S1,S2,S3,S4")
    run.add_argument("--apis", default="top,detail,search")
    run.add_argument("--vus", type=lambda v: [int(x) for x in v.split(",")], default=[50],
                     help="쉼표로 여러 개 (예: 10,50,100,200). 50이 기본 결과, 나머지는 -vuN 파일")
    run.add_argument("--duration", default="30s")
    run.add_argument("--warmup", default="10s")
    run.add_argument("--results", default="results",
                     help="결과 폴더 (perf/product-query 기준). 기존 결과를 덮지 않고 따로 잴 때 (예: results/p03)")
    run.set_defaults(func=cmd_run)
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
