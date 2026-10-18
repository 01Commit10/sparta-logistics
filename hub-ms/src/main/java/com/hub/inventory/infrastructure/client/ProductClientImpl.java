package com.hub.inventory.infrastructure.client;

import com.common.exception.CompanyErrorCode;
import com.common.exception.ProductErrorCode;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.hub.inventory.domain.client.ProductClient;
import com.hub.inventory.domain.client.ProductInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProductClientImpl implements ProductClient {
    private final CompanyFeignClient companyFeignClient;
    private final RemoteCallTemplate remoteCallTemplate;

    public ProductClientImpl(CompanyFeignClient companyFeignClient, RemoteCallTemplate remoteCallTemplate) {
        this.companyFeignClient = companyFeignClient;
        this.remoteCallTemplate = remoteCallTemplate;
    }

    private static final String TARGET ="company-ms";

    @Override
    public Optional<ProductInfo> findActiveProduct(UUID productId) {
        try {
            ProductFeignDto product = remoteCallTemplate.execute(
                    TARGET,
                    () -> companyFeignClient.getProduct(productId)
            );

            CompanyFeignDto company = remoteCallTemplate.execute(
                    TARGET,
                    () -> companyFeignClient.getCompany(product.companyId())
            );

            return Optional.of(new ProductInfo(
                    product.id(),
                    company.id(),
                    company.hubId(),
                    company.managerUserId()
            ));
        } catch (RemoteBusinessException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND.value()
                    && (e.getErrorCode() == ProductErrorCode.PRODUCT_NOT_FOUND
                    || e.getErrorCode() == CompanyErrorCode.COMPANY_NOT_FOUND)) {
                return Optional.empty();
            }

            throw e;
        }
    }
}
