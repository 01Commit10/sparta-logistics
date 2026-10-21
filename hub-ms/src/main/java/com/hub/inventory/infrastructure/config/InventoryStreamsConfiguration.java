package com.hub.inventory.infrastructure.config;
import com.hub.inventory.application.dto.InventoryResult;
import com.hub.inventory.application.service.InventoryLiveState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.UUID;
@Configuration(proxyBeanMethods=false)
public class InventoryStreamsConfiguration {
    @Bean @ConditionalOnProperty(name="inventory.stock.mode",havingValue="postgres",matchIfMissing=true)
    InventoryLiveState postgresInventoryLiveState() {
        return new InventoryLiveState() {
            public List<InventoryResult> refresh(List<InventoryResult> values) { return values; }
            public void registered(InventoryResult value) { }
            public void beforeDelete(UUID id) { }
        };
    }
}
