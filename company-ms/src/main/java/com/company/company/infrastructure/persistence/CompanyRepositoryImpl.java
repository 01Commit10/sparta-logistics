package com.company.company.infrastructure.persistence;

import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CompanyRepositoryImpl implements CompanyRepository {

    private final CompanyJpaRepository jpa;


    @Override
    public Company save(Company company) {
        return jpa.save(company);
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return jpa.findById(id);
    }
}
