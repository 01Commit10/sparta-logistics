package com.delivery.application.service;

import com.delivery.application.client.CompanyClient;
import com.delivery.application.client.HubRouteClient;
import com.delivery.application.client.UserClient;
import com.delivery.application.dto.command.DeliveryCreateCommand;
import com.delivery.domain.entity.Delivery;
import com.delivery.domain.repository.DeliveryRepository;
import com.delivery.domain.vo.CompanyInfo;
import com.delivery.domain.vo.DeliveryManagers;
import com.delivery.domain.vo.RouteEstimate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

/**
 * 배송 생성의 원격 조회(업체·경로·담당자·이름)는 트랜잭션 밖에서 해서, 기다리는 동안 DB 커넥션을 잡지 않는다 (P02).
 * 실제 @Transactional 프록시를 쓰려고 작은 스프링 컨텍스트에서 가짜 트랜잭션 매니저로 확인한다.
 */
@SpringJUnitConfig(DeliveryCreateTransactionTest.Config.class)
class DeliveryCreateTransactionTest {

    private static final UUID BUSAN = UUID.randomUUID();
    private static final UUID SEOUL = UUID.randomUUID();
    private static final UUID SUPPLIER = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID RECIPIENT = UUID.randomUUID();

    @Autowired
    private DeliveryCommandService deliveryCommandService;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private CompanyClient companyClient;
    @Autowired
    private HubRouteClient hubRouteClient;
    @Autowired
    private UserClient userClient;

    // 원격 호출마다 "그때 트랜잭션이 열려 있었는지"를 남긴다
    private final List<String> remoteCallsInTransaction = new ArrayList<>();

    @BeforeEach
    void setUp() {
        reset(deliveryRepository, companyClient, hubRouteClient, userClient);
        given(companyClient.findCompany(SUPPLIER)).willAnswer(i -> record("공급업체 조회",
                Optional.of(new CompanyInfo(BUSAN, "부산 중구", UUID.randomUUID()))));
        given(companyClient.findCompany(REQUESTER)).willAnswer(i -> record("요청업체 조회",
                Optional.of(new CompanyInfo(SEOUL, "서울 송파구", UUID.randomUUID()))));
        given(hubRouteClient.getRouteEstimate(BUSAN, SEOUL)).willAnswer(i -> record("경로 계산",
                new RouteEstimate(new BigDecimal("383.78"), 241)));
        given(userClient.pickDeliveryManagers(BUSAN, SEOUL)).willAnswer(i -> record("담당자 배정",
                new DeliveryManagers(UUID.randomUUID(), UUID.randomUUID())));
        given(userClient.getUserName(RECIPIENT)).willAnswer(i -> record("수령인 이름", "수령인"));
        given(deliveryRepository.save(any(Delivery.class))).willAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("배송 생성 중 원격 조회는 트랜잭션 밖에서 하고, 저장만 한다")
    void createDeliveryCallsRemoteServicesOutsideTransaction() {
        deliveryCommandService.createDelivery(
                new DeliveryCreateCommand(UUID.randomUUID(), BUSAN, REQUESTER, SUPPLIER, RECIPIENT));

        assertThat(remoteCallsInTransaction).as("트랜잭션 안에서 한 원격 호출").isEmpty();
    }

    private <T> T record(String call, T result) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            remoteCallsInTransaction.add(call);
        }
        return result;
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }

        @Bean
        DeliveryRepository deliveryRepository() {
            return mock(DeliveryRepository.class);
        }

        @Bean
        CompanyClient companyClient() {
            return mock(CompanyClient.class);
        }

        @Bean
        HubRouteClient hubRouteClient() {
            return mock(HubRouteClient.class);
        }

        @Bean
        UserClient userClient() {
            return mock(UserClient.class);
        }

        @Bean
        DeliveryCommandService deliveryCommandService(DeliveryRepository deliveryRepository, UserClient userClient,
                                                      CompanyClient companyClient, HubRouteClient hubRouteClient) {
            return new DeliveryCommandService(deliveryRepository, userClient, companyClient, hubRouteClient);
        }
    }
}
