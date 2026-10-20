-- P01 상품 조회 최적화 실험 데이터: 업체 100곳 + 상품 1,000,000개 (실험 전용, 다시 실행해도 중복 없음)
-- 업체 ID: c0000000-0000-4000-8000-000000000NNN (cleanup.sql이 이 범위만 지운다)
-- 상품 이름: "{지역} {품목} {규격} #{번호}" → 품목 40종이라 품목 검색 1번에 약 2.5만 건이 걸린다
-- 조회수: random()^12 × 200,000 → 대부분 0에 가깝고 소수 상품에 몰린다 (인기 상품이 뚜렷)

INSERT INTO company.p_companies (id, name, address, hub_id, manager_user_id, created_at, created_by)
SELECT format('c0000000-0000-4000-8000-%s', lpad(n::text, 12, '0'))::uuid,
       format('[PERF] 업체 %s', lpad(n::text, 3, '0')),
       format('서울 송파구 성능로 %s', n),
       format('10000000-0000-4000-8000-%s', lpad((((n - 1) % 17) + 1)::text, 12, '0'))::uuid,
       format('b0000000-0000-4000-8000-%s', lpad((((n - 1) % 4) + 1)::text, 12, '0'))::uuid,
       now() - (n || ' days')::interval,
       '00000000-0000-0000-0000-000000000000'::uuid
FROM generate_series(1, 100) AS n
ON CONFLICT (id) DO NOTHING;

INSERT INTO company.p_products (id, company_id, name, description, unit_price, view_count, created_at, created_by)
SELECT gen_random_uuid(),
       format('c0000000-0000-4000-8000-%s', lpad(((n % 100) + 1)::text, 12, '0'))::uuid,
       (ARRAY['서울','부산','대구','인천','광주','대전','울산','세종','경기','강원','충북','충남','전북','전남','경북','경남','제주'])[(n % 17) + 1]
           || ' ' ||
       (ARRAY['고등어','사과','배','감귤','한우','돼지고기','닭가슴살','쌀','현미','생수','우유','두부','계란','김치','고추장',
              '된장','참기름','라면','커피','녹차','오렌지주스','바나나','딸기','포도','수박','양파','감자','고구마','당근','배추',
              '버섯','새우','오징어','멸치','김','미역','햄','치즈','요거트','아이스크림'])[((n / 17) % 40) + 1]
           || ' ' ||
       (ARRAY['500g','1kg','2kg','5kg','10개입'])[((n / 680) % 5) + 1]
           || ' #' || n,
       '실험용 상품 설명',
       (1000 + (n % 200) * 500)::numeric(15, 2),
       floor(power(random(), 12) * 200000)::bigint,
       now() - ((n % 365) || ' days')::interval - ((n % 86400) || ' seconds')::interval,
       '00000000-0000-0000-0000-000000000000'::uuid
FROM generate_series(1, 1000000) AS n
WHERE NOT EXISTS (SELECT 1 FROM company.p_products p
                  WHERE p.company_id = 'c0000000-0000-4000-8000-000000000001'::uuid LIMIT 1);

ANALYZE company.p_companies;
ANALYZE company.p_products;
