package com.delivery.infrastructure.client.company;

import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.common.exception.DeliveryErrorCode;
import com.common.remote.RemoteBusinessException;
import com.common.remote.RemoteCallTemplate;
import com.delivery.application.client.CompanyClient;
import com.delivery.domain.vo.DeliveryDestination;
import com.delivery.infrastructure.client.company.dto.CompanyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CompanyClientImpl implements CompanyClient{

    private final RemoteCallTemplate remoteCallTemplate;
    private final CompanyFeignClient companyFeignClient;


    @Override
    public DeliveryDestination getDeliveryDestination(UUID companyId) {
        try {
            CompanyResponse companyResponse = remoteCallTemplate
                    .execute("company-ms", () -> companyFeignClient.getCompany(companyId));
            return new DeliveryDestination(companyResponse.hubId(), companyResponse.address());
        } catch (RemoteBusinessException e) {
            if(e.getErrorCode() == CompanyErrorCode.COMPANY_NOT_FOUND) {
                throw new BusinessException(DeliveryErrorCode.DELIVERY_INVALID_REQUESTER_COMPANY);
            }
            throw e;
        }
    }
}
