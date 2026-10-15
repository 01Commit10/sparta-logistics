package com.user.presentation.bootstrap;

import com.user.application.user.UserCommandService;
import com.user.application.user.dto.InitialMasterCommand;
import com.user.application.user.dto.InitialMasterResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * USR-19: 기동이 끝나면 초기 MASTER 생성을 요청한다.
 * HTTP 요청 대신 기동 이벤트로 유스케이스를 호출하는 입력 어댑터라 presentation에 둔다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(InitialMasterProperties.class)
public class InitialMasterInitializer implements ApplicationRunner {

    private final UserCommandService userCommandService;
    private final InitialMasterProperties properties;

    public InitialMasterInitializer(UserCommandService userCommandService, InitialMasterProperties properties) {
        this.userCommandService = userCommandService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        InitialMasterResult result = userCommandService.createInitialMasterIfAbsent(new InitialMasterCommand(
                properties.username(), properties.password(), properties.name(), properties.slackId()));
        switch (result) {
            case CREATED -> log.info("[InitialMaster] 초기 MASTER를 생성했습니다. username={}", properties.username());
            case ALREADY_EXISTS -> log.debug("[InitialMaster] 활성 MASTER가 있어 생성하지 않습니다.");
            case NOT_CONFIGURED -> log.warn("[InitialMaster] MASTER_* 환경 변수가 비어 있어 초기 MASTER를 만들지 않았습니다.");
        }
    }
}
