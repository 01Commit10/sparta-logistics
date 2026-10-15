package com.user.presentation.bootstrap;

import com.user.application.user.UserCommandService;
import com.user.application.user.dto.InitialMasterCommand;
import com.user.application.user.dto.InitialMasterResult;
import com.user.domain.user.PasswordHasher;
import com.user.domain.user.User;
import com.user.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기동하면 .env(여기서는 테스트 속성) 값으로 초기 MASTER가 1명 생기고, 다시 실행해도 늘지 않는다.
 */
@SpringBootTest(properties = {
        "initial-master.username=master01",
        "initial-master.password=Master!2345",
        "initial-master.name=관리자",
        "initial-master.slack-id=U00MASTER01"
})
class InitialMasterIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserCommandService userCommandService;

    @Autowired
    PasswordHasher passwordHasher;

    @Test
    @DisplayName("기동 후 초기 MASTER가 있고 비밀번호는 BCrypt로 저장된다")
    void createdOnStartup() {
        User master = userRepository.findActiveByUsername("master01").orElseThrow();

        assertThat(master.isMaster()).isTrue();
        assertThat(passwordHasher.matches("Master!2345", master.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("다시 실행해도 MASTER는 늘지 않는다 (재기동 상황)")
    void idempotent() {
        InitialMasterResult again = userCommandService.createInitialMasterIfAbsent(
                new InitialMasterCommand("master02", "Master!2345", "관리자2", "U00MASTER02"));

        assertThat(again).isEqualTo(InitialMasterResult.ALREADY_EXISTS);
        assertThat(userRepository.existsByUsername("master02")).isFalse();
    }
}
