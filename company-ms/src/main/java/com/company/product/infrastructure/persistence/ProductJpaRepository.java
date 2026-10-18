package com.company.product.infrastructure.persistence;

import com.company.product.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    @Query(value = "SELECT p FROM Product p WHERE p.company.id = :companyId AND p.deletedAt IS NULL",
            countQuery = "SELECT count(p) FROM Product p WHERE p.company.id = :companyId AND p.deletedAt IS NULL")
    Page<Product> findActiveByCompanyId(UUID companyId, Pageable query);
}
