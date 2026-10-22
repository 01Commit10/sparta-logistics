package com.company.company.infrastructure.persistence;

import com.company.company.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface CompanyJpaRepository extends JpaRepository<Company, UUID>, JpaSpecificationExecutor<Company> {
}
