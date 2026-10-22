package com.hub.inventory.domain.stream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
public interface InventoryReservationRepository {
    void bootstrap(PipelineSnapshot snapshot);
    PipelineState state();
    /** 현재 세대에 등록한 적 없는 재고만 DB 값으로 처음 등록할 수 있다. */
    void loadInventory(InventorySeed seed);
    boolean inventoryKnown(UUID hubId, UUID productId);
    InventoryStreamEvent execute(ReservationRequest request);
    Optional<InventoryStreamEvent> findOperation(UUID operationId);
    Map<UUID, Integer> quantities(List<UUID> inventoryIds);
    void tombstoneProduct(UUID productId);
    boolean isProductDeleted(UUID productId);
    /** Pending을 먼저 반환한다. 새 메시지도 없으면 첫 도착까지 제한된 시간 동안 기다린다. */
    List<StreamEntry> read(int count);
    /** 이 체크포인트를 DB에 커밋한 뒤에만 호출한다. */
    void confirm(UUID epoch, long sequence, String streamId);
    void pause();
    /** 생산·소비를 멈춘 상태에서 미확인 이벤트의 순서를 새 세대에 맞게 다시 구성한다. */
    void rebuild(PipelineSnapshot snapshot, List<StreamEntry> unconfirmed, UUID newEpoch);
    List<StreamEntry> unconfirmed();
}
