package com.hub.hub.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.hub.hub.application.service.HubService;
import com.hub.hub.domain.vo.HubSort;
import com.hub.hub.presentation.dto.HubResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hubs")
public class HubController {

    private final HubService hubService;

    public HubController(HubService hubService) {
        this.hubService = hubService;
    }

    //전체 허브 조회 및 이름 검색
    @GetMapping
    public List<HubResponseDto> getHubs(
            @RequestHeader(AuthContract.HEADER_USER_ID) UUID userId,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "sort", required = false) String sort
    ) {
        return hubService.getHubs(name, parseSort(sort))
                .stream()
                .map(HubResponseDto::from)
                .toList();
    }

    //단일 허브 조회
    @GetMapping("/{hubId}")
    public HubResponseDto getHub(
            @RequestHeader(AuthContract.HEADER_USER_ID) UUID userId,
            @PathVariable("hubId") UUID hubId
    ) {
        return HubResponseDto.from(hubService.getHub(hubId));
    }

    // HTTP 정렬 요청을 검증하고 허용된 도메인 정렬 값으로 변환한다.
    private HubSort parseSort(String sort) {
        if (sort == null) {
            return null;
        }

        return switch (sort) {
            case "createdAt,desc" -> HubSort.CREATED_AT_DESC;
            case "createdAt,asc" -> HubSort.CREATED_AT_ASC;
            case "name,asc" -> HubSort.NAME_ASC;
            case "name,desc" -> HubSort.NAME_DESC;
            default -> throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT,
                    "지원하지 않는 정렬 조건입니다."
            );
        };
    }

}
