package com.company.fake;

import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
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

    public Collection<Company> all() {
        return store.values();
    }
}
