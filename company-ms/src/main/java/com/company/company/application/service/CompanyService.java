package com.company.company.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.CompanyErrorCode;
import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.company.application.dto.CompanyResult;
import com.company.company.application.dto.CompanySearchQuery;
import com.company.company.application.dto.CreateCompanyCommand;
import com.company.company.application.dto.DeleteCompanyCommand;
import com.company.company.application.dto.UpdateCompanyCommand;
import com.company.company.domain.client.HubClient;
import com.company.company.domain.client.UserClient;
import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import com.company.company.domain.repository.CompanySearchCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserClient userClient;
    private final HubClient hubClient;
    private final TransactionOperations transactionOperations;

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

    // 업체 존재 → 권한 → 바뀌는 허브·관리자 원격 검증 → 저장 순서.
    // 원격 검증은 트랜잭션 밖에서 하고, 저장은 트랜잭션 안에서 업체를 다시 읽어 반영한다.
    // 처음 읽은(분리된) 엔티티를 그대로 저장하면, 원격 호출 사이에 삭제된 업체를 merge로 되살릴 수 있기 때문이다
    public CompanyResult updateCompany(UpdateCompanyCommand command) {
        Company company = findActiveCompany(command.companyId());
        validateCanUpdate(company, command);

        if (changesHub(company, command)) {
            validateHub(command.hubId());
        }
        if (changesManager(company, command)) {
            validateManager(command.managerUserId());
        }

        return transactionOperations.execute(status -> {
            Company target = findActiveCompany(command.companyId());
            validateCanUpdate(target, command);
            target.update(command.name(), command.address(), command.hubId(), command.managerUserId());
            return CompanyResult.from(companyRepository.save(target));
        });
    }

    // MASTER만 (04 API 명세서 Company, 권한은 Controller에서 확인). 원격 호출이 없어 조회·삭제를 한 트랜잭션으로 묶는다.
    // 삭제 시각·삭제자는 dirty checking으로 반영
    @Transactional
    public void deleteCompany(DeleteCompanyCommand command) {
        Company company = findActiveCompany(command.companyId());
        company.delete(command.actorId());
    }

    // MASTER 또는 업체 관리자 본인만 (07 서비스 정책 4장 권한).
    // 관리 허브·업체 관리자 변경은 MASTER만: 관리자가 스스로 권한을 넘기거나 관리 허브를 옮기지 못하게 한다 (등록도 MASTER 전용)
    private void validateCanUpdate(Company company, UpdateCompanyCommand command) {
        if (command.actorIsMaster()) {
            return;
        }
        if (!company.isManagedBy(command.actorId())
                || changesHub(company, command)
                || changesManager(company, command)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    // 현재 값과 같은 값을 보내면 바뀌지 않는 것으로 본다 (원격 검증·MASTER 전용 확인 생략)
    private boolean changesHub(Company company, UpdateCompanyCommand command) {
        return command.hubId() != null && !command.hubId().equals(company.getHubId());
    }

    private boolean changesManager(Company company, UpdateCompanyCommand command) {
        return command.managerUserId() != null && !command.managerUserId().equals(company.getManagerUserId());
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
