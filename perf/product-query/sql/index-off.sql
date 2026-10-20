-- 단계 S1: 실험 인덱스 없음 (기본 PK·company_id 인덱스만)
DROP INDEX IF EXISTS company.idx_perf_products_view_count;
DROP INDEX IF EXISTS company.idx_perf_products_name_trgm;
ANALYZE company.p_products;
