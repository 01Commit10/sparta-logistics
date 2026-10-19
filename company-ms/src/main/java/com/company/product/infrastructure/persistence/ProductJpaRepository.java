package com.company.product.infrastructure.persistence;

import com.company.product.domain.entity.Product;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    @Query(value = "SELECT p FROM Product p WHERE p.company.id = :companyId AND p.deletedAt IS NULL",
            countQuery = "SELECT count(p) FROM Product p WHERE p.company.id = :companyId AND p.deletedAt IS NULL")
    Page<Product> findActiveByCompanyId(UUID companyId, Pageable query);

    // 인기 상품: 페이지가 아니라 상위 N개라 count 쿼리가 없다.
    // 업체 조건 유무를 쿼리 두 개로 나눈다 (:companyId IS NULL OR ... 형태는 인덱스를 못 탈 수 있음)
    @Query("SELECT p FROM Product p JOIN p.company c WHERE p.deletedAt IS NULL AND c.deletedAt IS NULL "
            + "ORDER BY p.viewCount DESC, p.id ASC")
    List<Product> findPopular(Limit limit);

    @Query("SELECT p FROM Product p JOIN p.company c WHERE c.id = :companyId AND p.deletedAt IS NULL AND c.deletedAt IS NULL "
            + "ORDER BY p.viewCount DESC, p.id ASC")
    List<Product> findPopularByCompanyId(UUID companyId, Limit limit);

    // DB에서 바로 +1 하므로 동시 조회에도 누락이 없다. 벌크 UPDATE라 Auditing(updatedAt·updatedBy)은 바뀌지 않는다.
    // 영속성 컨텍스트를 거치지 않으므로 실행 후 비워서, 이어서 조회할 때 DB의 새 값을 읽게 한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void increaseViewCount(UUID id);
}
