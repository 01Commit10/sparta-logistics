package com.hub;

import com.hub.support.TestPostgresConnectConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=false"
})
@ActiveProfiles("test")
@Import(TestPostgresConnectConfig.class)
class HubMsApplicationTests {

    @Test
    void contextLoads() {
    }

}
