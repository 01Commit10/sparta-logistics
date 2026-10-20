-- 단계 S2~S4: 조회에 맞춘 인덱스
-- ① 인기 상품: ORDER BY view_count DESC, id ASC (PagePolicy가 id 오름차순을 붙인다), 삭제 안 된 상품만
CREATE INDEX IF NOT EXISTS idx_perf_products_view_count
    ON company.p_products (view_count DESC, id) WHERE deleted_at IS NULL;
-- ② 이름 부분 검색: lower(name) LIKE '%검색어%' 는 B-tree를 못 타므로 트라이그램 GIN
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_perf_products_name_trgm
    ON company.p_products USING gin (lower(name) gin_trgm_ops);
ANALYZE company.p_products;
