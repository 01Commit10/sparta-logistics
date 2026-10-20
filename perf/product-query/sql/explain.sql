-- 앱(Hibernate)이 보내는 것과 같은 모양의 쿼리 실행 계획. :'keyword' 는 psql 변수
\echo '=== ① 인기 상품 TOP 10'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT p.* FROM company.p_products p JOIN company.p_companies c ON c.id = p.company_id
WHERE p.deleted_at IS NULL AND c.deleted_at IS NULL
ORDER BY p.view_count DESC, p.id ASC OFFSET 0 LIMIT 10;
\echo '=== ①-count 전체 건수 (페이지 응답 totalElements)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT count(p.id) FROM company.p_products p JOIN company.p_companies c ON c.id = p.company_id
WHERE p.deleted_at IS NULL AND c.deleted_at IS NULL;
\echo '=== ② 상품 상세 (PK)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT * FROM company.p_products WHERE id = (SELECT id FROM company.p_products ORDER BY view_count DESC LIMIT 1);
\echo '=== ③ 이름 검색 (기본 정렬 createdAt desc, id)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT p.* FROM company.p_products p JOIN company.p_companies c ON c.id = p.company_id
WHERE p.deleted_at IS NULL AND c.deleted_at IS NULL AND lower(p.name) LIKE '%' || :'keyword' || '%' ESCAPE '\'
ORDER BY p.created_at DESC, p.id ASC OFFSET 0 LIMIT 10;
\echo '=== ③-count'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT count(p.id) FROM company.p_products p JOIN company.p_companies c ON c.id = p.company_id
WHERE p.deleted_at IS NULL AND c.deleted_at IS NULL AND lower(p.name) LIKE '%' || :'keyword' || '%' ESCAPE '\';
