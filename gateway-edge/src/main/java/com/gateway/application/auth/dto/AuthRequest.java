package com.gateway.application.auth.dto;

/** 인증·인가 판단에 필요한 요청 값. authorization은 Authorization 헤더 원문(없으면 null) */
public record AuthRequest(String method, String path, String authorization) {
}
