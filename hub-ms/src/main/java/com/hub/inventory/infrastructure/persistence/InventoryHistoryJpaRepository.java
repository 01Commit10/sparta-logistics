package com.hub.inventory.infrastructure.persistence;

import com.hub.inventory.domain.entity.InventoryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryHistoryJpaRepository
        extends JpaRepository<InventoryHistory, UUID> {
}