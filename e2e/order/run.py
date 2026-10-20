#!/usr/bin/env python3
"""주문 생성 E2E 시나리오: 실제로 띄운 서비스에 Gateway로 주문을 넣고, 응답과 DB 값을 함께 확인한다.

    python3 e2e/order/run.py                 # 전체 케이스
    python3 e2e/order/run.py S1 F4 C1        # 고른 케이스만
    python3 e2e/order/run.py --list          # 케이스 목록
    python3 e2e/order/run.py cleanup         # 이 스크립트가 만든 업체·상품·재고·주문·배송 지우기

- 저장소 루트에서 실행한다. 7개 서비스(eureka·gateway·user·hub·company·delivery·order)가 떠 있어야 한다.
- 장애 케이스(F5~F7)는 hub·user·delivery 프로세스를 잠시 멈추거나(SIGSTOP) 내렸다가 다시 띄운다.
  다시 띄울 때 `{module}/build/libs/*.jar`를 쓰므로 먼저 `sh ./gradlew bootJar`.
- .env에서 DB·MASTER 정보를 읽고 값은 출력하지 않는다.
- 결과: e2e/order/results/result.md (케이스별 기대·실제·DB 확인), result.json
"""
import concurrent.futures
import glob
import json
import os
import pathlib
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = ROOT / "e2e" / "order"
RESULTS = HERE / "results"
STATE = RESULTS / "created.json"   # cleanup용으로 만든 ID를 모아 둔다
GW = "http://localhost:19090"
PORTS = {"hub-ms": 19093, "user-ms": 19095, "delivery-ms": 19092, "order-ms": 19094, "company-ms": 19091}

SEOUL = "10000000-0000-4000-8000-000000000001"
BUSAN = "10000000-0000-4000-8000-000000000004"
COMP02 = "b0000000-0000-4000-8000-000000000002"   # 공급업체 담당자
COMP03 = "b0000000-0000-4000-8000-000000000003"   # 요청업체 담당자 (주문자)
PRICE = 5000


def load_env():
    env = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            env[key.strip()] = value.strip().strip('"').strip("'")
    return env


ENV = load_env()


# ---------- HTTP·DB ----------

def call(method, path, token=None, body=None, headers=None, timeout=30):
    h = {"Content-Type": "application/json", **(headers or {})}
    if token:
        h["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(GW + path, method=method, headers=h, data=data)
    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as res:
            raw = res.read().decode()
            status = res.status
    except urllib.error.HTTPError as e:
        raw, status = e.read().decode(), e.code
    try:
        payload = json.loads(raw) if raw else None
    except ValueError:
        payload = raw
    return Response(status, payload, time.time() - started)


class Response:
    def __init__(self, status, body, elapsed):
        self.status, self.body, self.elapsed = status, body, elapsed

    def get(self, key):
        return self.body.get(key) if isinstance(self.body, dict) else None

    def summary(self):
        b = self.body
        if isinstance(b, dict):
            keep = {k: b[k] for k in ("orderId", "orderStatus", "unitPriceSnapshot", "totalAmount", "code", "message")
                    if k in b}
            b = keep or b
        return f"{self.status} {json.dumps(b, ensure_ascii=False)[:160]} ({self.elapsed:.1f}s)"


def sql(query):
    cmd = ["docker", "exec", "-i", "-e", f"PGPASSWORD={ENV['DB_PASSWORD']}", "sparta-logistics-postgres",
           "psql", "-v", "ON_ERROR_STOP=1", "-U", ENV["DB_USER"], "-d", ENV["DB_NAME"], "-q", "-A", "-t", "-F", "|"]
    out = subprocess.run(cmd, input=query, text=True, capture_output=True, check=True).stdout.strip()
    return [line.split("|") for line in out.splitlines() if line]


def scalar(query):
    rows = sql(query)
    return rows[0][0] if rows else None


def login(username, password):
    res = call("POST", "/api/v1/auth/login", body={"username": username, "password": password})
    if res.status != 200:
        sys.exit(f"로그인 실패: {username} {res.status}")
    return res.get("accessToken")


# ---------- 프로세스 제어 ----------

def pids(module):
    out = subprocess.run(["lsof", "-ti", f"tcp:{PORTS[module]}", "-sTCP:LISTEN"], text=True,
                         capture_output=True).stdout
    return [int(p) for p in out.split()]


def pause(module):
    for pid in pids(module):
        os.kill(pid, signal.SIGSTOP)


def resume(module):
    for pid in pids(module):
        os.kill(pid, signal.SIGCONT)


def shutdown(module):
    for pid in pids(module):
        os.kill(pid, signal.SIGTERM)
    for _ in range(60):
        if not pids(module):
            return
        time.sleep(0.5)


def boot(module):
    jar = [j for j in glob.glob(str(ROOT / module / "build" / "libs" / f"{module}-*.jar"))
           if not j.endswith("-plain.jar")][0]
    RESULTS.mkdir(exist_ok=True)
    subprocess.Popen(["java", "-Xms256m", "-Xmx512m", "-jar", jar], cwd=ROOT, stdin=subprocess.DEVNULL,
                     stdout=open(RESULTS / f"{module}.log", "w"), stderr=subprocess.STDOUT, start_new_session=True)
    for _ in range(120):
        if pids(module):
            return
        time.sleep(1)
    sys.exit(f"{module}이 다시 뜨지 않았습니다: {RESULTS / f'{module}.log'}")


# ---------- 시나리오 준비 ----------

class World:
    """로그인 토큰, 이번 실행의 업체 두 곳. 상품·재고는 케이스마다 새로 만든다."""

    def __init__(self):
        self.master = login(ENV["MASTER_USERNAME"], ENV["MASTER_PASSWORD"])
        self.created = json.loads(STATE.read_text()) if STATE.exists() else {"companies": [], "products": []}
        tag = time.strftime("%m%d-%H%M%S")
        self.supplier = self.company(f"E2E 부산 수산물 {tag}", "부산 중구 자갈치해안로 52", BUSAN, COMP02)
        self.requester = self.company(f"E2E 서울 식품 {tag}", "서울 송파구 올림픽로 300", SEOUL, COMP03)
        for user, company in ((COMP02, self.supplier), (COMP03, self.requester)):
            res = call("PATCH", f"/api/v1/users/{user}/organization", self.master,
                       {"organizationType": "COMPANY", "organizationId": company})
            assert res.status == 200, res.summary()
        self.supplier_token = login("comp02", "Seed1234!")
        self.orderer = login("comp03", "Seed1234!")

    def company(self, name, address, hub, manager):
        res = call("POST", "/api/v1/companies", self.master,
                   {"name": name, "address": address, "hubId": hub, "managerUserId": manager})
        assert res.status == 201, res.summary()
        self.created["companies"].append(res.get("id"))
        self.save()
        return res.get("id")

    def product(self, stock, name="고등어 1kg"):
        res = call("POST", f"/api/v1/companies/{self.supplier}/products", self.supplier_token,
                   {"name": name, "description": "E2E", "unitPrice": PRICE})
        assert res.status == 201, res.summary()
        product = res.get("id")
        self.created["products"].append(product)
        self.save()
        res = call("POST", "/api/v1/inventories", self.master,
                   {"hubId": BUSAN, "productId": product, "initialQuantity": stock})
        assert res.status == 201, res.summary()
        return product

    def save(self):
        RESULTS.mkdir(exist_ok=True)
        STATE.write_text(json.dumps(self.created, indent=1))

    def body(self, product, quantity=3, **override):
        body = {"requesterCompanyId": self.requester, "supplierCompanyId": self.supplier, "productId": product,
                "quantity": quantity, "unitPrice": PRICE, "requestNote": "오전 배송 부탁드립니다"}
        body.update(override)
        return body

    def order(self, body, key=None, token="orderer", timeout=30):
        headers = {} if key is False else {"Idempotency-Key": key or str(uuid.uuid4())}
        return call("POST", "/api/v1/orders", self.orderer if token == "orderer" else token, body, headers, timeout)


# ---------- DB 조회 ----------

def stock(product):
    return int(scalar(f"select quantity from hub.p_inventories where product_id='{product}' and deleted_at is null;"))


def orders(product):
    return sql(f"select id, order_status, quantity, unit_price_snapshot, total_amount, coalesce(delivery_id::text,'') "
               f"from orders.p_orders where product_id='{product}' order by created_at;")


def history(key):
    rows = sql(f"select operation_status, response_code, coalesce(order_id::text,''), coalesce(last_error,'') "
               f"from orders.p_order_histories where idempotency_key='{key}';")
    return rows[0] if rows else None


def inventory_ops(product):
    return sql(f"select operation_type, operation_status, requested_quantity, coalesce(remaining_quantity::text,'') "
               f"from hub.p_inventory_operations where product_id='{product}' order by created_at;")


def deliveries(order_ids):
    if not order_ids:
        return []
    ids = ",".join(f"'{i}'" for i in order_ids)
    return sql(f"select id, order_id, delivery_status, departure_hub_id, arrival_hub_id, delivery_address, recipient_name, "
               f"recipient_user_id, coalesce(delivery_manager_id::text,'') from delivery.p_deliveries "
               f"where order_id in ({ids}) and deleted_at is null;")


def delivery_routes(delivery_id):
    return sql(f"select route_sequence, route_type, route_status, departure_hub_id, coalesce(arrival_hub_id::text,''), "
               f"estimated_distance_km, estimated_duration_min, coalesce(delivery_manager_id::text,'') "
               f"from delivery.p_delivery_routes where delivery_id='{delivery_id}' order by route_sequence;")


def hub_managers(hub):
    return [r[0] for r in sql(f"select id from auth.p_users where is_delivery_manager and deleted_at is null "
                              f"and organization_id='{hub}';")]


# ---------- 케이스 ----------

class Case:
    def __init__(self, cid, group, title, how):
        self.id, self.group, self.title, self.how = cid, group, title, how
        self.checks, self.responses, self.notes = [], [], []

    def check(self, label, expected, actual, ok=None):
        ok = (expected == actual) if ok is None else ok
        self.checks.append({"label": label, "expected": str(expected), "actual": str(actual), "ok": bool(ok)})

    def record(self, label, res):
        self.responses.append(f"{label}: {res.summary()}")
        return res

    @property
    def passed(self):
        return all(c["ok"] for c in self.checks)


CASES = []


def case(cid, group, title, how):
    def register(fn):
        CASES.append((Case(cid, group, title, how), fn))
        return fn
    return register


def no_order_created(c, product, before_stock):
    c.check("주문 행", 0, len(orders(product)))
    c.check("재고 그대로", before_stock, stock(product))
    c.check("재고 작업 기록", 0, len(inventory_ops(product)))


def compensated(c, w, product, key, res, code):
    """배송 단계 실패 → 재고 복원 → 주문·이력 FAILED"""
    c.check("응답 코드", code, res.status)
    rows = orders(product)
    c.check("주문 1건, FAILED", ["FAILED"], [r[1] for r in rows])
    h = history(key)
    c.check("이력 FAILED", "FAILED", h and h[0])
    c.check("재고 복원 (주문 전 100)", 100, stock(product))
    ops = [(r[0], r[1]) for r in inventory_ops(product)]
    c.check("재고 작업: 차감 성공 → 복원 성공", [("DECREASE", "SUCCEEDED"), ("RESTORE", "SUCCEEDED")], ops)
    c.check("배송 행", 0, len(deliveries([r[0] for r in rows])))
    return rows


@case("S1", "성공", "정상 주문", "재고 100, 3개 주문")
def s1(c, w):
    product = w.product(100)
    key = str(uuid.uuid4())
    res = c.record("주문", w.order(w.body(product), key))
    c.check("응답 코드", 201, res.status)
    c.check("응답 상태", "COMPLETED", res.get("orderStatus"))
    rows = orders(product)
    c.check("주문 행 1건", 1, len(rows))
    if not rows:
        return
    oid, status, qty, unit, total, delivery_id = rows[0]
    c.check("주문 상태", "COMPLETED", status)
    c.check("수량·단가·총액", ("3", "5000.00", "15000.00"), (qty, unit, total))
    c.check("배송 ID 저장", True, bool(delivery_id))
    h = history(key)
    c.check("이력 SUCCEEDED·201·주문 연결", ("SUCCEEDED", "201", oid), h and tuple(h[:3]))
    c.check("재고 100 → 97", 97, stock(product))
    ops = inventory_ops(product)
    c.check("재고 작업 DECREASE 성공 1건", [("DECREASE", "SUCCEEDED", "3", "97")], [tuple(r) for r in ops])
    key_used = scalar(f"select idempotency_key from hub.p_inventory_operations where product_id='{product}';")
    c.check("재고 차감 멱등키", f"order-{oid}-decrease", key_used)
    hist = sql(f"select change_type, stock_change, stock_after from hub.p_inventory_histories "
               f"where order_id='{oid}';")
    c.check("재고 변경 이력", [("DECREASE", "-3", "97")], [tuple(r) for r in hist])
    ds = deliveries([oid])
    c.check("배송 1건", 1, len(ds))
    if not ds:
        return
    d = ds[0]
    c.check("배송 ID = 주문의 배송 ID", delivery_id, d[0])
    c.check("배송 상태", "HUB_WAITING", d[2])
    c.check("출발 부산 → 도착 서울", (BUSAN, SEOUL), (d[3], d[4]))
    c.check("배송지 = 요청업체 주소", "서울 송파구 올림픽로 300", d[5])
    c.check("수령인 = 주문자(comp03)", COMP03, d[7])
    routes = delivery_routes(d[0])
    c.check("배송 구간 2개", ["HUB_TO_HUB", "HUB_TO_COMPANY"], [r[1] for r in routes])
    if len(routes) == 2:
        c.check("1구간 부산→서울 383.78km·241분", (BUSAN, SEOUL, "383.78", "241"), tuple(routes[0][3:7]))
        c.check("1구간 담당자 = 부산 허브 소속", True, routes[0][7] in hub_managers(BUSAN), )
        c.check("2구간 담당자 = 서울 허브 소속", True, routes[1][7] in hub_managers(SEOUL))
    w.s1 = {"product": product, "key": key, "order": oid}


@case("S8", "멱등성", "같은 키·같은 본문 재요청", "S1과 같은 Idempotency-Key·본문으로 다시 주문")
def s8(c, w):
    product, key, oid = w.s1["product"], w.s1["key"], w.s1["order"]
    res = c.record("재요청", w.order(w.body(product), key))
    c.check("응답 코드", 201, res.status)
    c.check("같은 주문 ID", oid, res.get("orderId"))
    c.check("주문 행 그대로 1건", 1, len(orders(product)))
    c.check("재고 그대로 97", 97, stock(product))
    c.check("배송 그대로 1건", 1, len(deliveries([oid])))


@case("S9", "멱등성", "같은 키·다른 본문", "S1과 같은 키로 수량 4")
def s9(c, w):
    product, key = w.s1["product"], w.s1["key"]
    res = c.record("재요청", w.order(w.body(product, quantity=4), key))
    c.check("응답 코드", 409, res.status)
    c.check("오류 코드", "IDEMPOTENCY_KEY_CONFLICT", res.get("code"))
    c.check("주문 행 그대로 1건", 1, len(orders(product)))
    c.check("재고 그대로 97", 97, stock(product))


@case("S10", "성공", "주문 후 상품 단가 변경", "comp02가 단가를 6000으로 수정")
def s10(c, w):
    product, oid = w.s1["product"], w.s1["order"]
    res = c.record("단가 수정", call("PATCH", f"/api/v1/products/{product}", w.supplier_token, {"unitPrice": 6000}))
    c.check("수정 응답", 200, res.status)
    row = sql(f"select unit_price_snapshot, total_amount from orders.p_orders where id='{oid}';")[0]
    c.check("기존 주문 금액 그대로", ("5000.00", "15000.00"), tuple(row))


@case("V1", "입력 검증", "수량 0", "quantity 0")
def v1(c, w):
    product = w.product(100)
    res = c.record("주문", w.order(w.body(product, quantity=0)))
    c.check("응답 코드", 400, res.status)
    no_order_created(c, product, 100)


@case("V2", "입력 검증", "Idempotency-Key 없음", "헤더 없이 주문")
def v2(c, w):
    product = w.product(100)
    res = c.record("주문", w.order(w.body(product), key=False))
    c.check("응답 코드", 400, res.status)
    no_order_created(c, product, 100)


@case("V3", "입력 검증", "로그인 안 함", "토큰 없이 주문")
def v3(c, w):
    product = w.product(100)
    res = c.record("주문", w.order(w.body(product), token=None))
    c.check("응답 코드", 401, res.status)
    no_order_created(c, product, 100)


@case("G1", "정책 검증", "단가를 요청값으로 조작", "상품 단가 5000인데 요청 unitPrice 1")
def g1(c, w):
    product = w.product(100)
    res = c.record("주문", w.order(w.body(product, unitPrice=1)))
    rows = orders(product)
    c.check("주문 단가 = 상품 단가 5000 (정책 5.1)", "5000.00", rows[0][3] if rows else None)
    c.notes.append("정책: 주문 시점 상품 단가를 Company Service에서 확인해 저장 (07 서비스 정책 5.1, 04 API 명세서)")


@case("G2", "정책 검증", "공급업체 소유가 아닌 상품", "supplierCompanyId에 요청업체 ID")
def g2(c, w):
    product = w.product(100)
    res = c.record("주문", w.order(w.body(product, supplierCompanyId=w.requester)))
    c.check("거절 (4xx)", "4xx", f"{res.status // 100}xx", ok=400 <= res.status < 500)
    no_order_created(c, product, 100)
    c.notes.append("정책: 상품이 공급업체 소유인지 검증 (07 서비스 정책 5.1)")


@case("G3", "정책 검증", "없는 상품", "임의 UUID 상품")
def g3(c, w):
    fake = str(uuid.uuid4())
    res = c.record("주문", w.order(w.body(fake)))
    c.check("응답 코드 404 (상품 없음)", 404, res.status)
    c.check("주문 행", 0, len(orders(fake)))


@case("F2", "실패", "재고 부족", "재고 100에 1000개 주문")
def f2(c, w):
    product = w.product(100)
    key = str(uuid.uuid4())
    res = c.record("주문", w.order(w.body(product, quantity=1000), key))
    c.check("응답 코드", 409, res.status)
    c.check("오류 코드", "INSUFFICIENT_STOCK", res.get("code"))
    no_order_created(c, product, 100)
    h = history(key)
    c.check("이력 FAILED·409", ("FAILED", "409"), h and tuple(h[:2]))
    w.f2 = {"product": product, "key": key}


@case("F3", "멱등성", "실패한 키로 재요청", "F2와 같은 키·본문으로 다시 주문")
def f3(c, w):
    product, key = w.f2["product"], w.f2["key"]
    res = c.record("재요청", w.order(w.body(product, quantity=1000), key))
    c.check("응답 코드", 409, res.status)
    c.check("오류 코드", "PREVIOUS_REQUEST_NOT_SUCCEEDED", res.get("code"))
    no_order_created(c, product, 100)


@case("F4", "실패", "배송담당자 없음 → 담당자 없이 배송 생성", "부산 허브 배송담당자를 모두 해제한 뒤 주문")
def f4(c, w):
    product = w.product(100)
    managers = hub_managers(BUSAN)
    try:
        for m in managers:
            r = call("PATCH", f"/api/v1/users/{m}/delivery-manager", w.master, {"isDeliveryManager": False})
            assert r.status == 200, r.summary()
        res = c.record("주문", w.order(w.body(product)))
        c.check("응답 코드", 201, res.status)
        c.check("주문 COMPLETED", "COMPLETED", res.get("orderStatus"))
        ds = deliveries([res.get("orderId")])
        c.check("배송 1건", 1, len(ds))
        if ds:
            c.check("배송·1구간 담당자 비어 있음 (나중에 수동 배정)", ("", ""),
                    (ds[0][8], delivery_routes(ds[0][0])[0][7]))
    finally:
        for m in managers:
            call("PATCH", f"/api/v1/users/{m}/delivery-manager", w.master, {"isDeliveryManager": True})
    c.notes.append("delivery-ms 설계: 후보가 없으면(DELIVERY_MANAGER_NOT_FOUND) 담당자 없이 배송을 만든다 "
                   "(`UserClientImpl.pickDeliveryManagers`). 07 서비스 정책 7장 '후보가 없으면 404'와 다르므로 문서 정리 필요")
    c.notes.append(f"부산 담당자 {len(managers)}명 해제 후 되돌림 (현재 {len(hub_managers(BUSAN))}명)")


@case("F5", "장애", "hub-ms 응답 없음", "hub-ms를 SIGSTOP으로 멈춘 채 주문")
def f5(c, w):
    product = w.product(100)
    key = str(uuid.uuid4())
    pause("hub-ms")
    try:
        res = c.record("주문", w.order(w.body(product), key))
    finally:
        resume("hub-ms")
    c.check("응답 코드", 503, res.status)
    c.check("오류 코드", "INVENTORY_UNAVAILABLE", res.get("code"))
    time.sleep(2)   # 멈춰 있던 요청이 뒤늦게 처리되는지 기다린다
    no_order_created(c, product, 100)
    h = history(key)
    c.check("이력 FAILED·503", ("FAILED", "503"), h and tuple(h[:2]))


@case("F6", "보상", "user-ms 응답 없음 → 재고 복원", "user-ms를 SIGSTOP (배송이 담당자 배정에서 막힘)")
def f6(c, w):
    product = w.product(100)
    key = str(uuid.uuid4())
    pause("user-ms")
    try:
        res = c.record("주문", w.order(w.body(product), key))
    finally:
        resume("user-ms")
    time.sleep(3)
    compensated(c, w, product, key, res, 503)


@case("F7", "보상", "delivery-ms 꺼짐 → 재고 복원", "delivery-ms를 내린 뒤 주문, 끝나면 다시 띄움")
def f7(c, w):
    product = w.product(100)
    key = str(uuid.uuid4())
    shutdown("delivery-ms")
    try:
        res = c.record("주문", w.order(w.body(product), key))
        compensated(c, w, product, key, res, 503)
    finally:
        boot("delivery-ms")
        wait_order_reaches_delivery(w)


@case("F8", "보상", "delivery-ms 응답 없음 (결과 모름)", "delivery-ms를 SIGSTOP한 채 주문, 응답 후 재개")
def f8(c, w):
    product = w.product(100)
    key = str(uuid.uuid4())
    pause("delivery-ms")
    try:
        res = c.record("주문", w.order(w.body(product), key))
    finally:
        resume("delivery-ms")
    time.sleep(5)   # 멈춰 있던 배송 생성 요청이 재개 뒤 처리되는지 본다
    rows = compensated(c, w, product, key, res, 503)
    c.notes.append("배송 행이 생기면: 주문은 FAILED·재고 복원인데 배송만 남는 고아 배송 (결과 모름 처리 필요)")
    _ = rows


@case("C1", "동시성", "재고 10개에 동시 주문 30건", "1개씩, 서로 다른 키로 동시에")
def c1(c, w):
    product = w.product(10)
    with concurrent.futures.ThreadPoolExecutor(30) as pool:
        results = list(pool.map(lambda _: w.order(w.body(product, quantity=1)), range(30)))
    codes = sorted(r.status for r in results)
    c.responses.append("응답 코드: " + ", ".join(f"{s}×{codes.count(s)}" for s in sorted(set(codes))))
    c.check("201 성공 10건", 10, codes.count(201))
    c.check("나머지 409", 20, codes.count(409))
    rows = orders(product)
    completed = [r for r in rows if r[1] == "COMPLETED"]
    c.check("COMPLETED 주문 10건", 10, len(completed))
    c.check("PROCESSING으로 남은 주문", 0, len([r for r in rows if r[1] == "PROCESSING"]))
    c.check("재고 0 (음수 아님)", 0, stock(product))
    c.check("배송 10건", 10, len(deliveries([r[0] for r in completed])))
    orphans(c, product)


@case("C2", "동시성", "같은 키로 동시 5건 (따닥)", "같은 Idempotency-Key·본문을 동시에")
def c2(c, w):
    product = w.product(100)
    key = str(uuid.uuid4())
    with concurrent.futures.ThreadPoolExecutor(5) as pool:
        results = list(pool.map(lambda _: w.order(w.body(product), key), range(5)))
    codes = sorted(r.status for r in results)
    c.responses.append("응답 코드: " + ", ".join(f"{s}×{codes.count(s)}" for s in sorted(set(codes))))
    c.check("5xx 없음", 0, len([s for s in codes if s >= 500]))
    rows = orders(product)
    c.check("주문 1건만", 1, len(rows))
    c.check("재고 3개만 차감", 97, stock(product))
    c.check("재고 차감 작업 1건만", 1, len(inventory_ops(product)))
    orphans(c, product)


def orphans(c, product):
    """주문 행 없이 남은 재고 차감·배송 (주문 트랜잭션이 롤백됐는데 다른 서비스 작업은 남은 것)"""
    op_orders = [r[0] for r in sql(f"select distinct order_id from hub.p_inventory_operations "
                                   f"where product_id='{product}';")]
    existing = {r[0] for r in orders(product)}
    missing = [o for o in op_orders if o not in existing]
    c.check("주문 없는 재고 차감", 0, len(missing))
    c.check("주문 없는 배송", 0, len(deliveries(missing)))


def wait_order_reaches_delivery(w, timeout=90):
    """delivery-ms를 다시 띄운 뒤 order-ms가 새 인스턴스를 찾을 때까지 (eureka 캐시 갱신) 기다린다."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        product = w.product(10, name="E2E 복구 확인")
        if w.order(w.body(product, quantity=1)).status == 201:
            return
        time.sleep(5)
    print("  ! delivery-ms 복구 확인 실패")


# ---------- 실행·기록 ----------

def write_result(done):
    RESULTS.mkdir(exist_ok=True)
    passed = sum(c.passed for c in done)
    lines = [f"# 주문 생성 E2E 결과 ({time.strftime('%Y-%m-%d %H:%M')})", "",
             f"{passed}/{len(done)} 케이스 통과 · 기준 커밋 `{git_head()}`", "",
             "| # | 구분 | 케이스 | 결과 | 실패한 확인 |", "|---|---|---|---|---|"]
    for c in done:
        failed = "; ".join(f"{x['label']}: 기대 {x['expected']} / 실제 {x['actual']}" for x in c.checks if not x["ok"])
        lines.append(f"| {c.id} | {c.group} | {c.title} | {'✅' if c.passed else '❌'} | {failed} |")
    for c in done:
        lines += ["", f"## {c.id} {c.title} {'✅' if c.passed else '❌'}", "", f"- 만드는 법: {c.how}"]
        lines += [f"- 응답 {r}" for r in c.responses]
        lines += [f"- 메모: {n}" for n in c.notes]
        lines += ["", "| 확인 | 기대 | 실제 | |", "|---|---|---|---|"]
        lines += [f"| {x['label']} | {x['expected']} | {x['actual']} | {'✅' if x['ok'] else '❌'} |" for x in c.checks]
    (RESULTS / "result.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (RESULTS / "result.json").write_text(json.dumps(
        [{"id": c.id, "title": c.title, "passed": c.passed, "responses": c.responses, "checks": c.checks,
          "notes": c.notes} for c in done], ensure_ascii=False, indent=1), encoding="utf-8")


def git_head():
    return subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, text=True, capture_output=True).stdout.strip()


def cleanup():
    if not STATE.exists():
        print("지울 데이터가 없습니다.")
        return
    created = json.loads(STATE.read_text())
    products = ",".join(f"'{p}'" for p in created["products"]) or "NULL"
    companies = ",".join(f"'{c}'" for c in created["companies"]) or "NULL"
    sql(f"""BEGIN;
UPDATE auth.p_users SET organization_id = NULL WHERE id IN ('{COMP02}','{COMP03}') AND organization_id IN ({companies});
DELETE FROM delivery.p_delivery_routes WHERE delivery_id IN (SELECT d.id FROM delivery.p_deliveries d
  JOIN orders.p_orders o ON o.id = d.order_id WHERE o.product_id IN ({products}));
DELETE FROM delivery.p_deliveries WHERE order_id IN (SELECT id FROM orders.p_orders WHERE product_id IN ({products}));
DELETE FROM orders.p_order_histories WHERE order_id IN (SELECT id FROM orders.p_orders WHERE product_id IN ({products}));
DELETE FROM orders.p_orders WHERE product_id IN ({products});
DELETE FROM hub.p_inventory_histories WHERE inventory_id IN (SELECT id FROM hub.p_inventories WHERE product_id IN ({products}));
DELETE FROM hub.p_inventory_operations WHERE product_id IN ({products});
DELETE FROM hub.p_inventories WHERE product_id IN ({products});
DELETE FROM company.p_products WHERE id IN ({products});
DELETE FROM company.p_companies WHERE id IN ({companies});
COMMIT;""")
    STATE.unlink()
    print(f"업체 {len(created['companies'])}개, 상품 {len(created['products'])}개와 딸린 주문·배송·재고를 지웠습니다.")
    print("주문 없이 남은 실패 이력(order_id NULL)은 남겨 둡니다. 필요하면 idempotency_key로 지우세요.")


def main():
    args = sys.argv[1:]
    if args == ["cleanup"]:
        return cleanup()
    if args == ["--list"]:
        for c, _ in CASES:
            print(f"{c.id:4} {c.group:6} {c.title} — {c.how}")
        return
    chosen = [(c, fn) for c, fn in CASES if not args or c.id in args]
    w = World()
    done = []
    for c, fn in chosen:
        print(f"{c.id:4} {c.title} ...", end=" ", flush=True)
        try:
            fn(c, w)
        except Exception as e:   # 케이스 하나가 깨져도 나머지는 계속
            c.check("실행 오류", "없음", f"{type(e).__name__}: {e}", ok=False)
        print("✅" if c.passed else "❌")
        for x in c.checks:
            if not x["ok"]:
                print(f"      ✗ {x['label']}: 기대 {x['expected']} / 실제 {x['actual']}")
        done.append(c)
    write_result(done)
    print(f"→ {RESULTS / 'result.md'}  ({sum(c.passed for c in done)}/{len(done)} 통과)")


if __name__ == "__main__":
    main()
