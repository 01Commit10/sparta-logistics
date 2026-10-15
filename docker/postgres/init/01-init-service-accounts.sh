#!/bin/bash
set -euo pipefail

# 서비스별 전용 계정 생성 + 자기 스키마만 소유하도록 권한 구성

psql_admin() {
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" "$@"
}

# 1) 기본 권한 회수: 아무나 DB 접속 / public 스키마 사용 불가
psql_admin -v db="$POSTGRES_DB" <<'EOSQL'
REVOKE CONNECT ON DATABASE :"db" FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
EOSQL

# 2) 서비스 계정 + 소유 스키마 생성
create_service_account() {
  local schema="$1" user="$2" password="$3"
  psql_admin -v db="$POSTGRES_DB" -v schema="$schema" -v svc_user="$user" -v svc_pw="$password" <<'EOSQL'
CREATE ROLE :"svc_user" LOGIN PASSWORD :'svc_pw' NOSUPERUSER NOCREATEDB NOCREATEROLE;
GRANT CONNECT ON DATABASE :"db" TO :"svc_user";
CREATE SCHEMA IF NOT EXISTS :"schema" AUTHORIZATION :"svc_user";
REVOKE ALL ON SCHEMA :"schema" FROM PUBLIC;
ALTER ROLE :"svc_user" SET search_path = :"schema";
EOSQL
}

create_service_account auth     "$AUTH_DB_USER"     "$AUTH_DB_PASSWORD"
create_service_account hub      "$HUB_DB_USER"      "$HUB_DB_PASSWORD"
create_service_account company  "$COMPANY_DB_USER"  "$COMPANY_DB_PASSWORD"
create_service_account orders   "$ORDER_DB_USER"    "$ORDER_DB_PASSWORD"
create_service_account delivery "$DELIVERY_DB_USER" "$DELIVERY_DB_PASSWORD"
