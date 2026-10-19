package com.company.company.presentation.controller;

import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 저장 → DB 값 변경(관리 허브 변경·업체 삭제) → 내부 API가 DB의 현재 상태를 그대로 돌려주는지 실제 빈으로 확인한다 (H2).
 * 사용자 헤더 확인 인터셉터 등 실제 MVC 설정을 거쳐도 헤더 없이 호출되는지도 함께 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalCompanyIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CompanyRepository companyRepository;

    private Company saveCompany() {
        return companyRepository.save(
                Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("헤더 없이 조회하면 DB 값이 그대로 나오고, 관리 허브 변경이 바로 반영된다")
    void reflectsDatabase() throws Exception {
        Company company = saveCompany();

        mockMvc.perform(get("/internal/v1/companies/{companyId}", company.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(company.getId().toString()))
                .andExpect(jsonPath("$.name").value("스파르타상사"))
                .andExpect(jsonPath("$.address").value("서울시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.hubId").value(company.getHubId().toString()))
                .andExpect(jsonPath("$.managerUserId").value(company.getManagerUserId().toString()));

        UUID newHubId = UUID.randomUUID();
        jdbc.update("update company.p_companies set hub_id = ? where id = ?", newHubId, company.getId());

        mockMvc.perform(get("/internal/v1/companies/{companyId}", company.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hubId").value(newHubId.toString()));
    }

    @Test
    @DisplayName("업체가 삭제(deleted_at 기록)되면 404 COMPANY_NOT_FOUND")
    void deletedCompany() throws Exception {
        Company company = saveCompany();

        jdbc.update("update company.p_companies set deleted_at = now() where id = ?", company.getId());

        mockMvc.perform(get("/internal/v1/companies/{companyId}", company.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    @DisplayName("없는 업체는 404 COMPANY_NOT_FOUND")
    void notFound() throws Exception {
        mockMvc.perform(get("/internal/v1/companies/{companyId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    @DisplayName("형식이 틀린 사용자 헤더는 내부 API에서도 401 (Gateway를 거치지 않은 위조 요청으로 본다)")
    void invalidUserHeader() throws Exception {
        Company company = saveCompany();

        mockMvc.perform(get("/internal/v1/companies/{companyId}", company.getId())
                        .header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized());
    }
}
