package com.company.company.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.company.application.dto.CompanyResult;
import com.company.company.application.dto.CompanySearchQuery;
import com.company.company.application.dto.CreateCompanyCommand;
import com.company.company.domain.client.HubClient;
import com.company.company.domain.client.UserClient;
import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import com.company.company.domain.repository.CompanySearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // 업체 단건 조회로 로그인 사용자 모두 접근 가능 (07 서비스 정책 4장 권한). 없거나 삭제된 업체는 404
    @Transactional(readOnly = true)
    public CompanyResult getCompany(UUID companyId) {
        return CompanyResult.from(findActiveCompany(companyId));
    }

    // 업체 목록·검색으로 로그인 사용자 모두 접근 가능 (07 서비스 정책 4장 권한).
    // hubId는 검색 조건이라 허브 존재를 hub-ms로 확인하지 않는다. 없는 허브면 빈 목록이다
    @Transactional(readOnly = true)
    public PageData<CompanyResult> searchCompanies(CompanySearchQuery searchQuery, PageQuery query) {
        CompanySearchCondition condition = new CompanySearchCondition(searchQuery.hubId(), searchQuery.name());
        return companyRepository.search(condition, query).map(CompanyResult::from);
    }

    // 삭제된 업체인지 검증하며 삭제된 업체일 시 404 반환
    private Company findActiveCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .filter(company -> !company.isDeleted())
                .orElseThrow(() -> new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));
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
