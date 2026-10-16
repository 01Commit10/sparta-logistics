package com.company.company.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.company.company.application.dto.CompanyResult;
import com.company.company.application.dto.CreateCompanyCommand;
import com.company.company.domain.client.HubClient;
import com.company.company.domain.client.UserClient;
import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserClient userClient;
    private final HubClient hubClient;

    // 원격 검증은 트랜잭션 밖에서 한다 (Feign 응답을 기다리는 동안 DB 커넥션을 잡지 않도록).
    // 쓰기는 save() 한 번이라 Repository 자체 트랜잭션으로 충분하다. 쓰기가 늘어나면 저장 부분만 트랜잭션으로 묶는다.
    public CompanyResult createCompany(CreateCompanyCommand command) {
        validateHub(command.hubId());
        validateManager(command.managerUserId());

        Company company = Company.create(
                command.name(),
                command.address(),
                command.hubId(),
                command.managerUserId()
        );
        return CompanyResult.from(companyRepository.save(company));
    }

    private void validateHub(UUID hubId) {
        if (!hubClient.existsHub(hubId)) {
            throw new BusinessException(CompanyErrorCode.COMPANY_HUB_NOT_FOUND);
        }
    }

    private void validateManager(UUID managerUserId) {
        if (userClient.findActiveUser(managerUserId).isEmpty()) {
            throw new BusinessException(CompanyErrorCode.COMPANY_MANAGER_NOT_FOUND);
        }
    }
}
