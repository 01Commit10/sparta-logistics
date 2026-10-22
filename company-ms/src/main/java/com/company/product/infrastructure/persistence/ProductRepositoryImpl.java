package com.company.product.infrastructure.persistence;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.common.page.SpringDataPages;
import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import com.company.product.domain.repository.ProductSearchCondition;
import com.company.product.domain.service.RankedProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpa;

    // 바로 flush해서 Auditing(updatedAt·updatedBy)과 DB 제약 위반을 응답을 만들기 전에 반영한다
    @Override
    public Product save(Product product) {
        return jpa.saveAndFlush(product);
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public PageData<Product> findActiveByCompanyId(UUID companyId, PageQuery query) {
        return SpringDataPages.toPageData(
                jpa.findActiveByCompanyId(companyId, SpringDataPages.toPageable(query))
        );
    }

    @Override
    public PageData<Product> search(ProductSearchCondition condition, PageQuery query) {
        return SpringDataPages.toPageData(
                jpa.findAll(ProductSpecifications.of(condition), SpringDataPages.toPageable(query))
        );
    }

    @Override
    public void increaseViewCount(UUID id) {
        jpa.increaseViewCount(id);
    }

    @Override
    public void applyViewCount(UUID id, long viewCount) {
        jpa.applyViewCount(id, viewCount);
    }

    @Override
    public List<RankedProduct> findTopByViewCount(int limit) {
        return jpa.findTopByViewCount(PageRequest.of(0, limit));
    }
}
