package com.company.company.infrastructure.persistence;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.common.page.SpringDataPages;
import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import com.company.company.domain.repository.CompanySearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CompanyRepositoryImpl implements CompanyRepository {

    private final CompanyJpaRepository jpa;

    // 바로 flush해서 Auditing(updatedAt·updatedBy)과 DB 제약 위반을 응답을 만들기 전에 반영한다
    @Override
    public Company save(Company company) {
        return jpa.saveAndFlush(company);
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public PageData<Company> search(CompanySearchCondition condition, PageQuery query) {
        return SpringDataPages.toPageData(
                jpa.findAll(CompanySpecifications.of(condition), SpringDataPages.toPageable(query))
        );
    }
}
