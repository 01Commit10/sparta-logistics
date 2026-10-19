package com.company.product.domain.repository;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.product.domain.entity.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(UUID id);
    PageData<Product> findActiveByCompanyId(UUID companyId, PageQuery query);
    PageData<Product> search(ProductSearchCondition condition, PageQuery query);
    // 조회수 상위 limit개 (조회수 같으면 id 순). companyId가 null이면 전체 업체. 삭제된 상품·업체는 제외
    List<Product> findPopular(UUID companyId, int limit);
    void increaseViewCount(UUID id);
}
