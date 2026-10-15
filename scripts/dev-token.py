#!/usr/bin/env python3
"""로컬 개발용 Access Token 발급 (로그인 API가 생기기 전 Gateway 인증 확인용).

.env의 JWT_SECRET_KEY로 docs/plan/02 인증 계약 2.1 형식(HS256)의 토큰을 만든다.
외부 라이브러리 없이 표준 라이브러리만 쓴다. 운영 키로는 쓰지 않는다.

  python3 scripts/dev-token.py                     # USER, 30분
  python3 scripts/dev-token.py --role MASTER
  python3 scripts/dev-token.py --expired           # 이미 만료된 토큰 (TOKEN_EXPIRED 확인용)
  python3 scripts/dev-token.py --typ refresh       # Refresh 오용 (TOKEN_INVALID 확인용)

  TOKEN=$(python3 scripts/dev-token.py --role MASTER)
  curl -i localhost:19090/api/v1/auth/routing-check -H "Authorization: Bearer $TOKEN"
"""
import argparse
import base64
import hashlib
import hmac
import json
import pathlib
import sys
import time
import uuid

ENV_FILE = pathlib.Path(__file__).resolve().parent.parent / ".env"
DEFAULT_USER_IDS = {
    "MASTER": "00000000-0000-0000-0000-00000000000a",
    "USER": "00000000-0000-0000-0000-00000000000b",
}


def read_secret():
    if not ENV_FILE.exists():
        sys.exit(f".env가 없습니다: {ENV_FILE}")
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        key, sep, value = line.partition("=")
        if sep and key.strip() == "JWT_SECRET_KEY":
            value = value.strip().strip('"').strip("'")
            if value:
                return base64.b64decode(value)
    sys.exit(".env에 JWT_SECRET_KEY가 없습니다.")


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def main():
    parser = argparse.ArgumentParser(description="로컬 개발용 Access Token 발급")
    parser.add_argument("--role", default="USER", help="MASTER 또는 USER (기본 USER)")
    parser.add_argument("--user-id", help="sub (기본: 역할별 고정 UUID)")
    parser.add_argument("--pcr", action="store_true", help="비밀번호 변경 필요(pcr=true)")
    parser.add_argument("--ttl", type=int, default=30, help="유효 시간(분, 기본 30)")
    parser.add_argument("--typ", default="access", help="토큰 종류 (기본 access)")
    parser.add_argument("--expired", action="store_true", help="31분 전에 발급한 만료 토큰")
    args = parser.parse_args()

    issued_at = int(time.time()) - (31 * 60 if args.expired else 0)
    payload = {
        "sub": args.user_id or DEFAULT_USER_IDS.get(args.role, DEFAULT_USER_IDS["USER"]),
        "role": args.role,
        "pcr": args.pcr,
        "typ": args.typ,
        "jti": str(uuid.uuid4()),
        "iat": issued_at,
        "exp": issued_at + args.ttl * 60,
    }
    header = {"alg": "HS256", "typ": "JWT"}
    signing_input = b64url(json.dumps(header, separators=(",", ":")).encode()) + "." + \
        b64url(json.dumps(payload, separators=(",", ":")).encode())
    signature = hmac.new(read_secret(), signing_input.encode(), hashlib.sha256).digest()
    print(signing_input + "." + b64url(signature))


if __name__ == "__main__":
    main()
