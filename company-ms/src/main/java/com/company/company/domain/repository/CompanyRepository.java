package com.company.company.domain.repository;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.company.domain.entity.Company;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository {
    Company save(Company company);
    Optional<Company> findById(UUID id);
    PageData<Company> search(CompanySearchCondition condition, PageQuery query);
}
