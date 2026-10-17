package com.company.product.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.CompanyErrorCode;
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

    private Company findActiveCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .filter(company -> !company.isDeleted())
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));
    }

    // MASTER 또는 해당 업체의 관리자 본인만 (07 서비스 정책 4장 권한)
    private void validateCanManage(Company company, CreateProductCommand command) {
        if (!command.actorIsMaster() && !company.isManagedBy(command.actorId())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
