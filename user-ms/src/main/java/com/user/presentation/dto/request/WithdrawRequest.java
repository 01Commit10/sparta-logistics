package com.user.presentation.dto.request;

import com.user.application.dto.WithdrawCommand;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/** 회원탈퇴 요청 (DELETE 본문). 본인 확인을 위해 비밀번호를 다시 받는다. 원문은 toString에서 가린다 */
public record WithdrawRequest(@NotBlank String password) {

    public WithdrawCommand toCommand(UUID userId) {
        return new WithdrawCommand(userId, password);
    }

    @Override
    public String toString() {
        return "WithdrawRequest[password=****]";
    }
}
