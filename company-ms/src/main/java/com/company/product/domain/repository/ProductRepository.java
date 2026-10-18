package com.company.product.domain.repository;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.product.domain.entity.Product;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(UUID id);
    PageData<Product> findActiveByCompanyId(UUID companyId, PageQuery query);
    PageData<Product> search(ProductSearchCondition condition, PageQuery query);
}
