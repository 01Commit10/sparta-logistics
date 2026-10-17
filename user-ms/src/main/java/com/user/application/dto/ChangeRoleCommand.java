package com.user.application.dto;

import java.util.UUID;

/** USR-14 역할 변경. role은 "MASTER"·"USER" (형식은 presentation이 검사) */
public record ChangeRoleCommand(UUID userId, String role) {
}
