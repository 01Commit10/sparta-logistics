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
import com.company.product.application.dto.DeleteProductCommand;
import com.company.product.application.dto.ProductResult;
import com.company.product.application.dto.UpdateProductCommand;
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
        validateCanManage(company, command.actorId(), command.actorIsMaster());

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

    // 상품·소속 업체 존재 확인 → 권한 확인 순서.
    // dirty checking만으로는 커밋 때 flush돼 응답의 updatedAt이 수정 전 값이므로 save(flush)로 반영한 뒤 응답한다
    @Transactional
    public ProductResult updateProduct(UpdateProductCommand command) {
        Product product = findActiveProduct(command.productId());
        Company company = findActiveCompany(product.getCompany().getId());
        validateCanManage(company, command.actorId(), command.actorIsMaster());

        product.update(command.name(), command.description(), command.unitPrice());
        return ProductResult.from(productRepository.save(product));
    }

    // 수정과 같은 순서로 확인. 삭제 시각·삭제자는 dirty checking으로 반영
    @Transactional
    public void deleteProduct(DeleteProductCommand command) {
        Product product = findActiveProduct(command.productId());
        Company company = findActiveCompany(product.getCompany().getId());
        validateCanManage(company, command.actorId(), command.actorIsMaster());

        product.delete(command.actorId());
    }

    // MASTER 또는 해당 업체의 관리자 본인만 (07 서비스 정책 4장 권한)
    private void validateCanManage(Company company, UUID actorId, boolean actorIsMaster) {
        if (!actorIsMaster && !company.isManagedBy(actorId)) {
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
