package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.user.application.dto.UserResult;
import com.user.application.dto.UserSearchQuery;
import com.user.application.facade.UserAdminFacade;
import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/users (U13 테스트 6·7, USR-11). 페이지 규칙은 common PagePolicy (size 1~100 밖 → 10, 허용 외 정렬 → 400).
 */
@WebMvcTest(UserAdminController.class)
class UserAdminControllerSearchTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserQueryService userQueryService;

    @MockitoBean
    UserCommandService userCommandService;

    @MockitoBean
    UserAdminFacade userAdminFacade;

    private static MockHttpServletRequestBuilder as(String role, UUID id) {
        return get("/api/v1/users")
                .header(AuthContract.HEADER_USER_ID, id.toString()).header(AuthContract.HEADER_USER_ROLE, role);
    }

    private static UserResult result() {
        return new UserResult(TARGET_ID, "hubman01", "홍길동", "U001", "USER", true, "HUB", "서울센터",
                UUID.fromString("00000000-0000-0000-0000-0000000000c1"), false, false,
                LocalDateTime.of(2026, 10, 15, 9, 0), MASTER_ID,
                LocalDateTime.of(2026, 10, 16, 9, 0), MASTER_ID,
                LocalDateTime.of(2026, 10, 16, 10, 0), MASTER_ID);
    }

    /** 서비스가 받은 pageable을 그대로 담아 돌려준다 (응답의 page·size·sort가 정규화 결과를 보여 주도록) */
    private void serviceEchoesPageable() {
        given(userQueryService.search(any(), any())).willAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1);
            return new PageImpl<>(List.of(result()), pageable, 21);
        });
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userQueryService).search(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("6. 파라미터 없음: 조건 없이, page 0·size 10·생성일 역순(+id)으로 조회한다")
    void defaults() throws Exception {
        serviceEchoesPageable();

        mockMvc.perform(as("MASTER", MASTER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.sort[0].property").value("createdAt"))
                .andExpect(jsonPath("$.sort[0].direction").value("DESC"))
                .andExpect(jsonPath("$.sort[1].property").value("id"));

        verify(userQueryService).search(new UserSearchQuery(null, null, null, null, null, null),
                PageRequest.of(0, 10, DEFAULT_SORT));
    }

    @Test
    @DisplayName("6. 목록 항목은 요약 필드만: Slack ID·소속 ID·작업자·비밀번호 관련 값은 없다")
    void summaryFields() throws Exception {
        serviceEchoesPageable();

        String body = mockMvc.perform(as("MASTER", MASTER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.content[0].username").value("hubman01"))
                .andExpect(jsonPath("$.content[0].name").value("홍길동"))
                .andExpect(jsonPath("$.content[0].role").value("USER"))
                .andExpect(jsonPath("$.content[0].isDeliveryManager").value(true))
                .andExpect(jsonPath("$.content[0].organizationType").value("HUB"))
                .andExpect(jsonPath("$.content[0].organizationName").value("서울센터"))
                .andExpect(jsonPath("$.content[0].active").value(false))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-10-15T09:00:00"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("slackId", "U001", "organizationId", "createdBy", "updatedBy", "deletedBy",
                "deletedAt", "updatedAt", "password", "hash");
    }

    @Test
    @DisplayName("6. 검색 조건·페이지·정렬 파라미터를 바인딩한다 (id 보조 정렬은 뒤에 붙는다)")
    void binding() throws Exception {
        serviceEchoesPageable();

        mockMvc.perform(as("MASTER", MASTER_ID)
                        .param("username", "hub").param("name", "홍").param("role", "USER")
                        .param("isDeliveryManager", "true").param("organizationType", "HUB")
                        .param("includeDeleted", "true")
                        .param("page", "2").param("size", "20").param("sort", "username,asc").param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(20));

        verify(userQueryService).search(new UserSearchQuery("hub", "홍", "USER", true, "HUB", true),
                PageRequest.of(2, 20, Sort.by(Sort.Order.asc("username"), Sort.Order.desc("name"), Sort.Order.asc("id"))));
    }

    @ParameterizedTest(name = "size={0} → 10")
    @ValueSource(strings = {"0", "-1", "101", "500"})
    @DisplayName("6. size가 1~100 밖이면 400이 아니라 기본 10으로 조회한다 (팀 공통 PagePolicy)")
    void sizeOutOfRange(String size) throws Exception {
        serviceEchoesPageable();

        mockMvc.perform(as("MASTER", MASTER_ID).param("size", size))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10));

        assertThat(capturedPageable().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("6. size 100은 그대로 쓴다 (상한)")
    void sizeMax() throws Exception {
        serviceEchoesPageable();

        mockMvc.perform(as("MASTER", MASTER_ID).param("size", "100"))
                .andExpect(status().isOk());

        assertThat(capturedPageable().getPageSize()).isEqualTo(100);
    }

    @ParameterizedTest(name = "sort={0} → 400")
    @ValueSource(strings = {"passwordHash,asc", "slackId", "role,desc", "deletedAt"})
    @DisplayName("6. 허용하지 않은 정렬 필드 → 400 INVALID_SORT_PROPERTY, 조회하지 않는다")
    void invalidSort(String sort) throws Exception {
        mockMvc.perform(as("MASTER", MASTER_ID).param("sort", sort))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT_PROPERTY"));

        verifyNoInteractions(userQueryService);
    }

    @ParameterizedTest(name = "{0}={1} → 400")
    @CsvSource({"role, ADMIN", "role, user", "organizationType, STORE", "isDeliveryManager, maybe", "includeDeleted, abc"})
    @DisplayName("6. 조건 값 형식이 틀리면 400 INVALID_INPUT, 조회하지 않는다")
    void invalidCondition(String param, String value) throws Exception {
        mockMvc.perform(as("MASTER", MASTER_ID).param(param, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(userQueryService);
    }

    @Test
    @DisplayName("7. USER면 403 ACCESS_DENIED, 조회하지 않는다")
    void user_forbidden() throws Exception {
        mockMvc.perform(as("USER", TARGET_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(userQueryService);
    }

    @Test
    @DisplayName("7. X-User-* 헤더가 없으면 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userQueryService);
    }
}
