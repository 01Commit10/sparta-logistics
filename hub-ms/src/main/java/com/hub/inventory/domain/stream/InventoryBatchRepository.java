package com.hub.inventory.domain.stream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
/** DB access only. The application owns bootstrap and batch transaction boundaries. */
public interface InventoryBatchRepository {
    Optional<PipelineCheckpoint> checkpoint();
    PipelineSnapshot snapshot(UUID epochWhenNew);
    Optional<InventorySeed> inventory(UUID hubId, UUID productId);
    void start(UUID epoch);
    void prepareRecovery(UUID expectedEpoch, UUID newEpoch);
    void switchEpoch(UUID expectedEpoch, UUID newEpoch);
    PipelineCheckpoint persist(List<StreamEntry> entries);
}
