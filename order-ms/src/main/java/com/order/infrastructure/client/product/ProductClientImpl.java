package com.order.infrastructure.client.product;

import com.common.remote.RemoteCallTemplate;
import com.order.domain.client.ProductClient;
import com.order.domain.client.ProductInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("!stub")
@RequiredArgsConstructor
public class ProductClientImpl implements ProductClient {
    private static final String TARGET = "company-ms";

    private final ProductFeignClient productFeignClient;
    private final RemoteCallTemplate remoteCallTemplate;


    @Override
    public ProductInfo getProduct(UUID productId) {
        ProductFeignResponse product = remoteCallTemplate.execute(TARGET,
                ()-> productFeignClient.getProduct(productId));
        return new ProductInfo(product.id(), product.companyId(), product.name(), product.unitPrice());
    }
}
