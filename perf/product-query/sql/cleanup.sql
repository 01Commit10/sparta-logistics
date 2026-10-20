-- 실험 데이터·인덱스 지우기 (실험 업체 범위만)
DROP INDEX IF EXISTS company.idx_perf_products_view_count;
DROP INDEX IF EXISTS company.idx_perf_products_name_trgm;
DELETE FROM company.p_products
WHERE company_id BETWEEN 'c0000000-0000-4000-8000-000000000001'::uuid AND 'c0000000-0000-4000-8000-000000000100'::uuid;
DELETE FROM company.p_companies
WHERE id BETWEEN 'c0000000-0000-4000-8000-000000000001'::uuid AND 'c0000000-0000-4000-8000-000000000100'::uuid;
