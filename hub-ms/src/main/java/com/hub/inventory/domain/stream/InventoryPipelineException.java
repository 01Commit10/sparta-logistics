package com.hub.inventory.domain.stream;
/** Infrastructure safety gate or business conflict translated by the application boundary. */
public class InventoryPipelineException extends RuntimeException {
    public enum Reason { UNAVAILABLE, IDEMPOTENCY_CONFLICT, ALREADY_RESTORED, ORIGINAL_NOT_FOUND, INVALID_ORIGINAL, INVENTORY_NOT_FOUND, QUANTITY_LIMIT }
    private final Reason reason;
    public InventoryPipelineException(Reason reason) { super(reason.name()); this.reason = reason; }
    public InventoryPipelineException(Reason reason, Throwable cause) { super(reason.name(), cause); this.reason = reason; }
    public Reason reason() { return reason; }
}
