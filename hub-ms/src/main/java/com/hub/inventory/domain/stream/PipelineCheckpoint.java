package com.hub.inventory.domain.stream;
import java.util.UUID;
/** A durable recovery target permits a restart between Redis rebuild and PostgreSQL epoch switch. */
public record PipelineCheckpoint(UUID epoch, long sequence, String streamId, UUID recoveryEpoch) {
    public PipelineCheckpoint(UUID epoch,long sequence,String streamId) { this(epoch,sequence,streamId,null); }
}
