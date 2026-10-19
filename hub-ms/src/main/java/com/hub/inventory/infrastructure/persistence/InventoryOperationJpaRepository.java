package com.hub.inventory.infrastructure.persistence;

import com.hub.inventory.domain.entity.InventoryOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryOperationJpaRepository extends JpaRepository<InventoryOperation, UUID> {
}
