package com.company.product.infrastructure.persistence;

import com.company.product.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    @Query(value = "SELECT p FROM Product p WHERE p.company.id = :companyId AND p.deletedAt IS NULL",
            countQuery = "SELECT count(p) FROM Product p WHERE p.company.id = :companyId AND p.deletedAt IS NULL")
    Page<Product> findActiveByCompanyId(UUID companyId, Pageable query);

    // DB에서 바로 +1 하므로 동시 조회에도 누락이 없다. 벌크 UPDATE라 Auditing(updatedAt·updatedBy)은 바뀌지 않는다.
    // 영속성 컨텍스트를 거치지 않으므로 실행 후 비워서, 이어서 조회할 때 DB의 새 값을 읽게 한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void increaseViewCount(UUID id);
}
