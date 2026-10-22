package com.hub.inventory.domain.stream;

import java.util.List;

/** Retains the decodable prefix so a poison transport record cannot block earlier valid work. */
public class InvalidInventoryStreamEventException extends InventoryPipelineException {
    private final List<StreamEntry> validPrefix;
    private final String streamId;
    public InvalidInventoryStreamEventException(List<StreamEntry> validPrefix,String streamId,Throwable cause) {
        super(Reason.UNAVAILABLE,cause);
        this.validPrefix=List.copyOf(validPrefix);
        this.streamId=streamId;
    }
    public List<StreamEntry> validPrefix() { return validPrefix; }
    @Override public String getMessage() { return "Malformed inventory Stream event at "+streamId; }
}
