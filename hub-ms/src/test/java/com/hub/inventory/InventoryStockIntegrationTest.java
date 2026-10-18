package com.hub.inventory;

import com.hub.support.TestPostgresConnectConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=false",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=true"
})
@AutoConfigureMockMvc
@Import(TestPostgresConnectConfig.class)
public class InventoryStockIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID hubId;
    private UUID productId;
    private UUID inventoryId;

    @BeforeEach
    void setUp() {
        hubId = UUID.randomUUID();
        productId = UUID.randomUUID();
        inventoryId = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO hub.p_hubs (
                    id, name, address, created_at
                )
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """,
                hubId,
                "재고 변경 테스트 허브 -"+ hubId,
                "테스트 주소"
        );

        jdbcTemplate.update("""
                INSERT INTO hub.p_inventories (
                    id, hub_id, product_id, quantity, created_at
                )
                VALUES (?, ?, ?, 100, CURRENT_TIMESTAMP)
                """,
                inventoryId,
                hubId,
                productId
        );
    }

    @Test
    @DisplayName("재고 100개에서 30개 차감하면 재고 70개")
    void decreaseStock() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/inventories/decrease")
                .header(
                        "Idempotency-Key",
                        "order:" + orderId + ":decrease"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                          {
                           "orderId": "%s",
                           "hubId": "%s",
                           "productId": "%s",                         
                           "quantity": 30                                                                            
                          }                         
                          """.formatted(orderId, hubId, productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.inventoryId").value(inventoryId.toString()))
                .andExpect(jsonPath("$.remainingQuantity").value(70));

        Integer storedQuantity = jdbcTemplate.queryForObject("""
                SELECT quantity
                FROM hub.p_inventories
                WHERE id = ?                
                """,
                Integer.class,
                inventoryId
                );

        Long operationCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM hub.p_inventory_operations
                WHERE order_id = ?
                AND inventory_id = ?
                AND operation_type = 'DECREASE'
                AND operation_status = 'SUCCEEDED'
                AND requested_quantity = 30
                AND remaining_quantity = 70
                """,
                Long.class,
                orderId,
                inventoryId

        );

        assertThat(storedQuantity).isEqualTo(70);
        assertThat(operationCount).isEqualTo(1L);
    }






}

