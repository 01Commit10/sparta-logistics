package com.company.fake;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import com.company.product.domain.repository.ProductSearchCondition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 테스트용 메모리 저장소. ID는 JPA(@GeneratedValue)가 만드는 것을 흉내 내 저장할 때 채운다. */
public class FakeProductRepository implements ProductRepository {

    private final Map<UUID, Product> store = new LinkedHashMap<>();

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    // 정렬은 무시하고 저장 순서대로 자른다. 정렬은 @DataJpaTest(ProductRepositoryImplTest)에서 확인
    @Override
    public PageData<Product> findActiveByCompanyId(UUID companyId, PageQuery query) {
        List<Product> matched = store.values().stream()
                .filter(product -> product.getCompany().getId().equals(companyId))
                .filter(product -> !product.isDeleted())
                .toList();
        List<Product> content = matched.stream().skip(query.offset()).limit(query.size()).toList();
        return new PageData<>(content, matched.size());
    }

    // 정렬은 무시하고 저장 순서대로 자른다. 정렬·대소문자·와일드카드 처리는 @DataJpaTest(ProductRepositorySearchTest)에서 확인
    @Override
    public PageData<Product> search(ProductSearchCondition condition, PageQuery query) {
        List<Product> matched = store.values().stream()
                .filter(product -> !product.isDeleted())
                .filter(product -> !product.getCompany().isDeleted())
                .filter(product -> condition.companyId() == null
                        || product.getCompany().getId().equals(condition.companyId()))
                .filter(product -> condition.name() == null
                        || product.getName().toLowerCase(Locale.ROOT).contains(condition.name().toLowerCase(Locale.ROOT)))
                .toList();
        List<Product> content = matched.stream().skip(query.offset()).limit(query.size()).toList();
        return new PageData<>(content, matched.size());
    }

    // 실제 구현처럼 조회수 내림차순 → id 오름차순으로 정렬해 limit개만. id 순서는 Java UUID 비교라 DB와 다를 수 있다
    @Override
    public List<Product> findPopular(UUID companyId, int limit) {
        return store.values().stream()
                .filter(product -> !product.isDeleted())
                .filter(product -> !product.getCompany().isDeleted())
                .filter(product -> companyId == null || product.getCompany().getId().equals(companyId))
                .sorted(Comparator.comparingLong(Product::getViewCount).reversed().thenComparing(Product::getId))
                .limit(limit)
                .toList();
    }

    // 실제 구현(벌크 UPDATE)처럼 Auditing 필드는 건드리지 않고 조회수만 올린다. 없는 ID는 아무 일도 하지 않는다
    @Override
    public void increaseViewCount(UUID id) {
        Product product = store.get(id);
        if (product != null) {
            ReflectionTestUtils.setField(product, "viewCount", product.getViewCount() + 1);
        }
    }

    public Collection<Product> all() {
        return store.values();
    }
}
