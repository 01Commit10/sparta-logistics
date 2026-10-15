package com.user.presentation.bootstrap;

import com.user.application.user.UserCommandService;
import com.user.application.user.dto.InitialMasterCommand;
import com.user.application.user.dto.InitialMasterResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialMasterInitializerTest {

    @Test
    @DisplayName("기동 시 .env 값을 명령으로 바꿔 초기 MASTER 생성을 요청한다")
    void delegatesToService() throws Exception {
        UserCommandService service = mock(UserCommandService.class);
        when(service.createInitialMasterIfAbsent(any())).thenReturn(InitialMasterResult.CREATED);
        InitialMasterProperties properties =
                new InitialMasterProperties("master01", "Master!2345", "관리자", "U00MASTER01");

        new InitialMasterInitializer(service, properties).run(new DefaultApplicationArguments());

        verify(service).createInitialMasterIfAbsent(
                new InitialMasterCommand("master01", "Master!2345", "관리자", "U00MASTER01"));
    }
}
