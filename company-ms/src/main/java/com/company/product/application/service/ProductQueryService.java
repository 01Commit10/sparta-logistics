package com.company.product.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.common.exception.ProductErrorCode;
import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.company.domain.repository.CompanyRepository;
import com.company.product.application.cache.ProductCacheNames;
import com.company.product.application.dto.ProductResult;
import com.company.product.application.dto.ProductSearchQuery;
import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import com.company.product.domain.repository.ProductSearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 사용자 상품 조회 (상세·목록·검색). 결과를 캐시에 담는다 (P01 실험: product.cache.mode가 none이면 캐시 없음).
 * 내부 API(주문 검증)는 주문 시점 단가가 필요해 캐시하지 않고 ProductService.getProduct를 쓴다.
 * 404(예외)는 캐시하지 않는다.
 * sync = true: miss일 때 Cache.get(key, loader)로 읽어, hot 모드의 키 단위 single-flight(SingleFlightCache)가 스탬피드를 막는다 (P03).
 */
@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;

    // 상품·소속 업체가 살아 있는지 확인한 상세. 조회수는 캐시 값이 아니라 ProductViewCounter 값으로 응답한다
    @Cacheable(cacheNames = ProductCacheNames.DETAIL, key = "#productId", sync = true)
    @Transactional(readOnly = true)
    public ProductResult getActiveProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
        companyRepository.findById(product.getCompany().getId())
                .filter(company -> !company.isDeleted())
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));
        return ProductResult.from(product);
    }

    // 검색 조건 + 페이지(번호·크기·정렬)가 같으면 같은 결과. 수정·삭제해도 지우지 않고 짧은 TTL로만 갱신한다
    @Cacheable(cacheNames = ProductCacheNames.SEARCH, key = "#searchQuery + '|' + #pageQuery", sync = true)
    @Transactional(readOnly = true)
    public PageData<ProductResult> searchProducts(ProductSearchQuery searchQuery, PageQuery pageQuery) {
        ProductSearchCondition condition = new ProductSearchCondition(searchQuery.companyId(), searchQuery.name());
        return productRepository.search(condition, pageQuery).map(ProductResult::from);
    }
}
