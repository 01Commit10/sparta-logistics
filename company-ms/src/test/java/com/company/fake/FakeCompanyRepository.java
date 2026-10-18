package com.company.fake;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import com.company.company.domain.repository.CompanySearchCondition;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 테스트용 메모리 저장소. ID는 JPA(@GeneratedValue)가 만드는 것을 흉내 내 저장할 때 채운다. */
public class FakeCompanyRepository implements CompanyRepository {

    private final Map<UUID, Company> store = new LinkedHashMap<>();

    @Override
    public Company save(Company company) {
        if (company.getId() == null) {
            ReflectionTestUtils.setField(company, "id", UUID.randomUUID());
        }
        store.put(company.getId(), company);
        return company;
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    // 정렬은 무시하고 저장 순서대로 자른다. 정렬·대소문자·와일드카드 처리는 @DataJpaTest(CompanyRepositorySearchTest)에서 확인
    @Override
    public PageData<Company> search(CompanySearchCondition condition, PageQuery query) {
        List<Company> matched = store.values().stream()
                .filter(company -> !company.isDeleted())
                .filter(company -> condition.hubId() == null || company.getHubId().equals(condition.hubId()))
                .filter(company -> condition.name() == null
                        || company.getName().toLowerCase(Locale.ROOT).contains(condition.name().toLowerCase(Locale.ROOT)))
                .toList();
        List<Company> content = matched.stream().skip(query.offset()).limit(query.size()).toList();
        return new PageData<>(content, matched.size());
    }

    public Collection<Company> all() {
        return store.values();
    }
}
