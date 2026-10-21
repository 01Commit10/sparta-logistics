package com.company.product.domain.repository;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.product.domain.entity.Product;
import com.company.product.domain.service.RankedProduct;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(UUID id);
    PageData<Product> findActiveByCompanyId(UUID companyId, PageQuery query);
    PageData<Product> search(ProductSearchCondition condition, PageQuery query);
    void increaseViewCount(UUID id);
    // Redis에 모은 조회수를 DB에 반영. 지금 값보다 클 때만 바꿔 줄어들지 않는다
    void applyViewCount(UUID id, long viewCount);
    // 조회수 상위 limit개 (삭제 제외). hot 모드 기동 시 순위 초기화용 (P03)
    List<RankedProduct> findTopByViewCount(int limit);
}
