package com.hub.inventory.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** Consumer progress is committed together with Inventory, Operation and History. */
@Entity
@Table(name="p_inventory_stream_checkpoints",schema="hub")
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class InventoryStreamCheckpoint {
    @Id @Column(length=40) private String pipeline;
    @Column(nullable=false) private UUID epoch;
    @Column(name="recovery_epoch") private UUID recoveryEpoch;
    @Column(name="last_sequence",nullable=false) private long lastSequence;
    @Column(name="last_stream_id",nullable=false,length=40) private String lastStreamId;
}
