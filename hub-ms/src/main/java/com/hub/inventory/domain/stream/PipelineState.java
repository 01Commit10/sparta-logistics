package com.hub.inventory.domain.stream;
import java.util.UUID;
public record PipelineState(UUID epoch, Status status, long revision, long confirmedSequence,
        String confirmedStreamId, long oldestUnconfirmedAtMillis) {
    public enum Status { READY, DIRTY, PAUSED, MISSING }
    public long backlog() { return revision - confirmedSequence; }
}
