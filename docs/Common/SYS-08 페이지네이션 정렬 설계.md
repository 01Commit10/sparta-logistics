# SYS-08. 페이지네이션·정렬 설계

> 작성일: 2026-10-16 · 최종 수정: 2026-10-17 (순수 페이지 타입 추가) · 담당: 권우 (공통 모듈) · 각 서비스 담당 (목록 API 적용)
> 관련: [[요구사항 정리]] SYS-08 · [[07 서비스 정책]] 1.5 조회·검색 · [[04 API 명세서]] 공통 API 설계 규칙 · [[SA 설계보완리포트]] A-4
> 따라 하기: [[페이지네이션 사용법]]

## 1. 목적과 범위

모든 목록·검색 API가 **같은 파라미터로 요청받고 같은 형식으로 응답**하도록 공통 규칙과 구현을 정합니다.

| 영역 | 내용 | 담당 |
|---|---|---|
| 공통 응답 | `PageResponse<T>`, `SortOrder` | 권우 |
| 요청 정규화 | `PagePolicy` (size 범위, 정렬 허용 목록, 기본 정렬, 보조 정렬) | 권우 |
| 순수 페이지 타입 | `PageQuery`, `PageData`, `SpringDataPages` (domain에서 Spring을 금지한 서비스용) | 권우 |
| 목록 API 적용 | 서비스별 정렬 허용 필드 지정, Repository 조회 구현 | 각 서비스 담당 |

**범위 밖:** 검색 조건(필터) 형식, 커서 기반 페이지네이션, count 쿼리를 생략하는 Slice 응답 (필요해지면 같은 패키지에 추가).

**구현 위치:** `common/src/main/java/com/common/page/`

---

## 2. 요청 규칙

| 파라미터 | 규칙 | 범위를 벗어나면 |
|---|---|---|
| `page` | **0부터 시작** | 음수 → 0 (Spring 리졸버 기본 동작) |
| `size` | 1~100, 기본값 10 | 0·음수·100 초과 → **10** (400을 내지 않음) |
| `sort` | `필드명,방향` (`asc`/`desc`), 여러 번 반복 가능 | 허용 목록에 없는 필드 → **400 `INVALID_SORT_PROPERTY`** |

- 정렬을 지정하지 않으면 **생성일 역순 (`createdAt DESC`)** 입니다. `createdAt`은 `BaseEntity` 필드라 모든 API에서 항상 정렬할 수 있습니다.
- 모든 정렬의 마지막에 **`id ASC`가 보조 정렬로 자동으로 붙습니다.** `createdAt`이 같은 행끼리 순서가 고정되지 않으면 페이지를 넘길 때 데이터가 중복되거나 빠질 수 있기 때문입니다. 요청에 `id` 정렬이 이미 있으면 붙이지 않습니다.
- 마지막 페이지를 넘는 `page`는 에러가 아니라 빈 `content`로 응답합니다.

### 여러 필드 정렬

`sort`를 **우선순위 순서대로 반복**해서 보냅니다. 요청한 순서가 그대로 적용됩니다.

```
GET /api/v1/companies?page=0&size=30&sort=type,asc&sort=name,asc&sort=createdAt,desc
→ 정렬: type ASC → name ASC → createdAt DESC → id ASC
```

**주의:** `sort=name,type,desc`처럼 하나의 파라미터에 필드를 여러 개 쓰면 방향(`desc`)이 모든 필드에 적용됩니다. 필드마다 방향이 다르면 `sort`를 나눠서 보냅니다.

---

## 3. 응답 형식

```json
{
  "content": [ { "id": "...", "name": "스파르타상사" } ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5,
  "hasNext": true,
  "sort": [
    { "property": "createdAt", "direction": "DESC" },
    { "property": "id", "direction": "ASC" }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `content` | 배열 | 현재 페이지 데이터 |
| `page` | int | 현재 페이지 번호 (0부터) |
| `size` | int | 실제로 적용된 페이지 크기 |
| `totalElements` | long | 전체 건수 |
| `totalPages` | int | 전체 페이지 수 |
| `hasNext` | boolean | 다음 페이지가 있는지 |
| `sort` | 배열 | **실제로 적용된 정렬** (기본 정렬·보조 정렬 포함) |

- `size`와 `sort`는 요청값이 아니라 **정규화 후 실제 적용된 값**입니다. 클라이언트는 `size=150`을 보냈어도 응답의 `size: 10`으로 무엇이 적용됐는지 알 수 있습니다.
- Spring의 `Page`(`PageImpl`)를 그대로 반환하지 않습니다. `pageable`, `sort.empty` 같은 내부 구조가 노출되고, Feign으로 받을 때 역직렬화가 어렵기 때문입니다.
- 서비스 간 Feign 호출에서도 응답 타입으로 `PageResponse<T>`를 그대로 씁니다.

### 에러 응답

```json
{ "code": "INVALID_SORT_PROPERTY", "message": "정렬할 수 없는 필드입니다. (요청: password, 허용: [createdAt, name])" }
```

---

## 4. 구성 요소

| 클래스 | 역할 |
|---|---|
| `PageResponse<T>` | 목록 공통 응답 record. `from(Page)`, `from(Page, mapper)`, `from(PageData, PageQuery, mapper)` |
| `SortOrder` | 응답에 담는 정렬 한 건 `{ property, direction }` |
| `PagePolicy` | API별 정렬 허용 목록을 갖고 `Pageable`을 정규화. `of(String...)`, `apply(Pageable)` → `Pageable`, `toQuery(Pageable)` → `PageQuery` |
| `PageQuery` | 정규화된 페이지 요청 `{ page, size, orderList }`. 정렬 한 건은 `PageOrder(property, ascending)`. `offset()` 제공. **Spring 의존 없음** |
| `PageData<T>` | 조회 결과 한 페이지 `{ content, totalElements }`. `map()`은 content만 변환. **Spring 의존 없음** |
| `SpringDataPages` | infrastructure 전용 변환기. `toPageable(PageQuery)`, `toPageData(Page)` |
| `CommonErrorCode.INVALID_SORT_PROPERTY` | 허용되지 않은 정렬 필드 (400) |

`application-common.yaml`

```yaml
spring:
  data:
    web:
      pageable:
        default-page-size: 10
```

`max-page-size`는 **일부러 설정하지 않습니다.** 100으로 두면 `size=150`이 리졸버에서 먼저 100으로 줄어든 뒤 허용 범위로 통과해, "범위 밖은 10"이라는 규칙과 어긋납니다. size 제한은 `PagePolicy`가 맡습니다.

### `PagePolicy.apply(Pageable)` 처리 순서

1. `Pageable`이 없거나 unpaged면 `page 0`, `size 10`, 기본 정렬
2. size가 1~100 밖이면 10으로 변경
3. 정렬이 없으면 `createdAt DESC`, 있으면 모든 필드가 허용 목록에 있는지 검사 (하나라도 없으면 400)
4. 마지막에 `id ASC` 보조 정렬 추가

`toQuery(Pageable)`은 `apply`를 그대로 호출한 뒤 결과를 `PageQuery`로 옮기기만 합니다. 정규화 규칙이 두 메서드에서 항상 같습니다.

### `PageResponse.from(PageData, PageQuery, mapper)` 값의 출처

| 응답 필드 | 출처 |
|---|---|
| `content`, `totalElements` | `PageData` |
| `page`, `size`, `sort` | `PageQuery` (Controller가 만든 것을 그대로 다시 넘김) |
| `totalPages` | `ceil(totalElements / size)` |
| `hasNext` | `page + 1 < totalPages` |

`PageImpl`과 같은 공식이라 `from(Page, mapper)`와 응답이 같습니다. 단 `PageImpl`은 마지막 페이지에서 `offset + 실제 건수`로 전체 건수를 보정하고, 이 메서드는 count 쿼리 값을 그대로 씁니다. 내용 조회와 count 조회 사이에 데이터가 바뀐 순간에만 차이가 날 수 있습니다.

---

## 5. 계층별 적용 방법

계층을 지나는 타입에 따라 두 방식이 있습니다. **서비스의 ArchitectureTest가 domain에서 `org.springframework..` 의존을 막으면 B**, 아니면 A를 씁니다. 두 방식은 같은 DB 쿼리와 같은 응답을 만듭니다 (6장 테스트).

**방식 A. Spring 타입**

```
presentation : Pageable 수신 → PagePolicy.apply() → Service 호출 → PageResponse.from(page, Response::from)
application  : Pageable을 받아 Page<Result> 반환 (Entity → Result 변환)
domain       : Repository 인터페이스가 Pageable을 받아 Page<Entity> 반환
infrastructure: JPA / QueryDSL 구현 (조회·count 쿼리 모두 deleted_at IS NULL)
```

**방식 B. 순수 타입** (company-ms, user-ms처럼 domain에서 Spring을 금지한 서비스)

```
presentation : Pageable 수신 → PagePolicy.toQuery() → Service 호출 → PageResponse.from(data, query, Response::from)
application  : PageQuery를 받아 PageData<Result> 반환 (변환 없음)
domain       : Repository 인터페이스가 PageQuery를 받아 PageData<Entity> 반환
infrastructure: SpringDataPages.toPageable(query)로 JPA 호출 → SpringDataPages.toPageData(page)
```

- `PageResponse`는 **presentation에서만** 만듭니다. application은 `Page<Result>`(A) 또는 `PageData<Result>`(B)까지만 반환합니다.
- 방식 B에서 Spring 타입은 presentation 입구(`PagePolicy`)와 infrastructure(`SpringDataPages`)에서만 다룹니다. `PageQuery`·`PageData`에는 Spring import를 넣지 않습니다.
- `PagePolicy`·`PageResponse`·`SpringDataPages`는 Spring에 의존하지만 패키지가 `com.common.page`라서, `org.springframework..`만 검사하는 ArchitectureTest로는 잡히지 않습니다. 방식 B를 쓰는 서비스는 이 셋의 사용 계층을 제한하는 규칙을 추가합니다 ([[페이지네이션 사용법]] 7장).
- user-ms는 common 타입이 생기기 전에 자체 `PageQuery`·`PageData`(`com.user.domain.repository`)로 방식 B를 구현했습니다. 그대로 동작하며, common 타입으로 옮기는 것은 선택입니다.

아래 예시는 방식 A입니다. 방식 B의 4개 파일 예시는 [[페이지네이션 사용법]] 2장에 있습니다.

### Controller

`PagePolicy`는 **API별 상수**로 둡니다. 같은 서비스여도 API마다 정렬 허용 필드가 다를 수 있습니다.

```java
private static final PagePolicy PAGE_POLICY = PagePolicy.of("name", "type");

@GetMapping
public PageResponse<CompanyResponse> searchCompanies(@CurrentUser AuthUser authUser,
                                                     @ModelAttribute CompanySearchRequest request,
                                                     Pageable pageable) {
    Page<CompanyResult> page = companyService.searchCompanies(request.toCondition(), PAGE_POLICY.apply(pageable));
    return PageResponse.from(page, CompanyResponse::from);
}
```

**`PAGE_POLICY.apply()`를 빠뜨리면 size 제한과 정렬 검사가 모두 적용되지 않습니다.** 리뷰 시 확인합니다.

### Application

```java
@Transactional(readOnly = true)
public Page<CompanyResult> searchCompanies(CompanySearchCondition condition, Pageable pageable) {
    return companyRepository.search(condition, pageable).map(CompanyResult::from);
}
```

### Infrastructure

- **Spring Data JPA 메서드**(`findAllByDeletedAtIsNull(Pageable)` 등)는 `Pageable`의 정렬이 자동으로 적용됩니다.
- **QueryDSL**은 정렬을 자동으로 적용하지 않습니다. `Sort.Order`를 `OrderSpecifier`로 바꿔 `orderBy`에 넘겨야 합니다. 정렬 필드는 이미 허용 목록으로 걸러졌으므로 `PathBuilder`로 그대로 변환해도 안전합니다.
- count 쿼리를 따로 작성할 때 **`deleted_at IS NULL` 조건을 빠뜨리지 않습니다.** 빠지면 `totalElements`가 틀어집니다.

### 정렬 허용 필드 정하기

- 정렬 필드명은 **엔티티 필드명**과 같아야 합니다 (`hub.name` 같은 연관 필드·별칭은 현재 지원하지 않음).
- 데이터가 많은 테이블이면 정렬 필드에 인덱스가 있는지 확인합니다 ([[SA 설계보완리포트]] E-3).

---

## 6. 테스트

| 위치 | 내용 |
|---|---|
| `common` `PagePolicyTest` | size 경계값(1·100 유지, 101·150·2000 → 10), 기본 정렬, 여러 필드 정렬 순서, 보조 정렬 중복 방지, 허용되지 않은 필드 400. `toQuery`가 `apply`와 같은 규칙을 따르는지 |
| `common` `PageResponseTest` | 페이지 정보·정렬 매핑, mapper 변환, 범위 밖 page, JSON 직렬화·역직렬화. `PageData` 방식의 totalPages·hasNext 경계값, **`PageData` 방식 응답 = `Page` 방식 응답** |
| `common` `PageQueryTest` | 잘못된 page·size 거부, `offset` 계산(int 범위 초과 포함), 정렬 목록 불변 |
| `common` `PageDataTest` | `map`이 전체 건수 유지, content 불변, 빈 페이지 |
| `common` `SpringDataPagesTest` | 정렬 순서·방향 보존, unsorted, `Page` → `PageData`, **`toQuery` → `toPageable` = `apply` 결과** |
| 각 서비스 presentation | 목록 API의 기본값·정렬·400 응답 (`@WebMvcTest`) |
| 각 서비스 infrastructure | 정렬·페이지 결과, 삭제 데이터 제외 및 count 일치 (`@DataJpaTest`) |
