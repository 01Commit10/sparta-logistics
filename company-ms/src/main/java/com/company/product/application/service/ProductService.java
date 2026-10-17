package com.company.product.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.CompanyErrorCode;
import com.common.exception.ProductErrorCode;
import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import com.company.product.application.dto.CreateProductCommand;
import com.company.product.application.dto.ProductResult;
import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;

    // 업체 조회와 상품 저장을 한 트랜잭션으로 (원격 호출 없음)
    @Transactional
    public ProductResult createProduct(CreateProductCommand command) {
        Company company = findActiveCompany(command.companyId());
        validateCanManage(company, command);

        Product product = Product.create(
                company,
                command.name(),
                command.description(),
                command.unitPrice()
        );
        return ProductResult.from(productRepository.save(product));
    }

    // 상품 단건 조회로 로그인 사용자 모두 접근 가능 (07 서비스 정책 4장 권한)
    @Transactional(readOnly = true)
    public ProductResult getProduct(UUID productId) {
        Product product = findActiveProduct(productId);
        findActiveCompany(product.getCompany().getId());
        return ProductResult.from(product);
    }

    @Transactional(readOnly = true)
    public PageData<ProductResult> getProductsByCompany(UUID companyId, PageQuery query) {
        findActiveCompany(companyId);
        return productRepository.findActiveByCompanyId(companyId, query).map(ProductResult::from);
    }

    // MASTER 또는 해당 업체의 관리자 본인만 (07 서비스 정책 4장 권한)
    private void validateCanManage(Company company, CreateProductCommand command) {
        if (!command.actorIsMaster() && !company.isManagedBy(command.actorId())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    // 삭제된 업체인지 검증하며 삭제된 업체일 시 404 반환
    private Company findActiveCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .filter(company -> !company.isDeleted())
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));
    }

    // 삭제된 상품인지 검증하며 삭제된 상품일 시 404 반환
    private Product findActiveProduct(UUID productId) {
        return productRepository.findById(productId)
                .filter(product -> !product.isDeleted())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
