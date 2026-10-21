package com.hub.inventory.infrastructure.persistence;

import com.hub.inventory.domain.stream.*;
import com.hub.inventory.domain.vo.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Native batch writes preserve the same ledger/history invariants as the synchronous JPA path. */
@Repository
public class PostgresInventoryBatchRepository implements InventoryBatchRepository {
    private static final String PIPELINE = "inventory";
    private final JdbcTemplate jdbc;
    public PostgresInventoryBatchRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<PipelineCheckpoint> checkpoint() {
        return jdbc.query("SELECT epoch,last_sequence,last_stream_id,recovery_epoch FROM hub.p_inventory_stream_checkpoints WHERE pipeline=?",
                checkpointMapper(), PIPELINE).stream().findFirst();
    }
    private RowMapper<PipelineCheckpoint> checkpointMapper() {
        return (rs,n) -> new PipelineCheckpoint(rs.getObject("epoch",UUID.class),rs.getLong("last_sequence"),rs.getString("last_stream_id"),rs.getObject("recovery_epoch",UUID.class));
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void start(UUID epoch) {
        jdbc.update("INSERT INTO hub.p_inventory_stream_checkpoints(pipeline,epoch,last_sequence,last_stream_id) VALUES(?,?,0,'0-0')", PIPELINE,epoch);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void prepareRecovery(UUID expectedEpoch, UUID newEpoch) {
        require(jdbc.update("UPDATE hub.p_inventory_stream_checkpoints SET recovery_epoch=? WHERE pipeline=? AND epoch=?",
                newEpoch,PIPELINE,expectedEpoch)==1,"Recovery checkpoint changed");
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void switchEpoch(UUID expectedEpoch, UUID newEpoch) {
        require(jdbc.update("UPDATE hub.p_inventory_stream_checkpoints SET epoch=?,last_sequence=0,last_stream_id='0-0',recovery_epoch=NULL WHERE pipeline=? AND epoch=? AND recovery_epoch=?",
                newEpoch,PIPELINE,expectedEpoch,newEpoch)==1,"Recovery checkpoint changed");
    }
    @Override public Optional<InventorySeed> inventory(UUID hubId, UUID productId) {
        return jdbc.query("SELECT id,hub_id,product_id,quantity,deleted_at IS NOT NULL AS deleted FROM hub.p_inventories WHERE hub_id=? AND product_id=?",
                seedMapper(),hubId,productId).stream().findFirst();
    }
    private RowMapper<InventorySeed> seedMapper() {
        return (rs,n)->new InventorySeed(rs.getObject("id",UUID.class),rs.getObject("hub_id",UUID.class),rs.getObject("product_id",UUID.class),rs.getInt("quantity"),rs.getBoolean("deleted"));
    }
    @Override public PipelineSnapshot snapshot(UUID epochWhenNew) {
        PipelineCheckpoint cp=checkpoint().orElse(new PipelineCheckpoint(epochWhenNew,0,"0-0"));
        List<InventorySeed> inventories=jdbc.query("SELECT id,hub_id,product_id,quantity,deleted_at IS NOT NULL AS deleted FROM hub.p_inventories ORDER BY id",seedMapper());
        List<InventoryStreamEvent> operations=jdbc.query("SELECT * FROM hub.p_inventory_operations ORDER BY created_at,id",(rs,n)->event(rs,cp.epoch()));
        return new PipelineSnapshot(cp.epoch(),cp.sequence(),cp.streamId(),inventories,operations);
    }
    private InventoryStreamEvent event(ResultSet rs, UUID epoch) throws SQLException {
        return new InventoryStreamEvent(epoch,0,rs.getObject("id",UUID.class),rs.getString("idempotency_key"),
                rs.getObject("original_operation_id",UUID.class),rs.getObject("order_id",UUID.class),rs.getObject("hub_id",UUID.class),
                rs.getObject("product_id",UUID.class),rs.getObject("inventory_id",UUID.class),InventoryOperationType.valueOf(rs.getString("operation_type")),
                InventoryOperationStatus.valueOf(rs.getString("operation_status")),rs.getString("rejection_reason")==null?null:InventoryRejectionReason.valueOf(rs.getString("rejection_reason")),
                rs.getInt("requested_quantity"),rs.getObject("remaining_quantity",Integer.class),null,
                rs.getObject("created_at",LocalDateTime.class),rs.getObject("created_at",LocalDateTime.class),rs.getObject("created_by",UUID.class),
                rs.getObject("updated_at",LocalDateTime.class),rs.getObject("updated_by",UUID.class));
    }

    @Override @Transactional(propagation=Propagation.MANDATORY)
    public PipelineCheckpoint persist(List<StreamEntry> entries) {
        require(!entries.isEmpty(),"Empty batch");
        PipelineCheckpoint cp=jdbc.query("SELECT epoch,last_sequence,last_stream_id,recovery_epoch FROM hub.p_inventory_stream_checkpoints WHERE pipeline=? FOR UPDATE",checkpointMapper(),PIPELINE)
                .stream().findFirst().orElseThrow(()->new IllegalStateException("Pipeline is not initialized"));
        require(cp.recoveryEpoch()==null,"Pipeline recovery is in progress");
        long expected=cp.sequence(); String lastId=cp.streamId();
        Set<UUID> ids=new HashSet<>();
        List<InventoryStreamEvent> fresh=new ArrayList<>(), replay=new ArrayList<>();
        for(StreamEntry entry:entries) {
            InventoryStreamEvent e=entry.event(); validate(e);
            require(cp.epoch().equals(e.epoch()),"Different pipeline epoch");
            require(ids.add(e.operationId()),"Duplicate ID in one batch");
            if(e.sequence()<=cp.sequence()) { replay.add(e); continue; }
            require(e.sequence()==++expected,"Non-contiguous event sequence");
            require(compareIds(entry.streamId(),lastId)>0,"Non-increasing Stream ID");
            lastId=entry.streamId(); fresh.add(e);
        }
        verifyExisting(replay);
        if(fresh.isEmpty()) return cp;
        Set<UUID> inserted=insertOperations(fresh);
        verifyExisting(fresh.stream().filter(e->!inserted.contains(e.operationId())).toList());
        List<InventoryStreamEvent> added=fresh.stream().filter(e->inserted.contains(e.operationId())).toList();
        validateOriginals(added);
        List<InventoryStreamEvent> changes=added.stream().filter(e->e.stockChange()!=0).toList();
        List<InventoryStreamEvent> snapshots=added.stream().filter(e->e.inventoryId()!=null).toList();
        if(!snapshots.isEmpty()) updateStocks(snapshots);
        if(!changes.isEmpty()) insertHistories(changes);
        jdbc.update("UPDATE hub.p_inventory_stream_checkpoints SET last_sequence=?,last_stream_id=? WHERE pipeline=?",expected,lastId,PIPELINE);
        return new PipelineCheckpoint(cp.epoch(),expected,lastId);
    }
    private static void validate(InventoryStreamEvent e) {
        require(e!=null && e.epoch()!=null && e.sequence()>0 && e.operationId()!=null,"Missing event identity");
        require(e.idempotencyKey()!=null && !e.idempotencyKey().isBlank() && e.idempotencyKey().length()<=120,"Invalid idempotency key");
        require(e.orderId()!=null && e.hubId()!=null && e.productId()!=null && e.requestedQuantity()>0 && e.occurredAt()!=null && e.createdAt()!=null,"Invalid event request/audit");
        require(e.operationType()==InventoryOperationType.DECREASE || e.operationType()==InventoryOperationType.RESTORE,"Invalid operation type");
        require(e.operationStatus()==InventoryOperationStatus.SUCCEEDED || e.operationStatus()==InventoryOperationStatus.REJECTED,"Invalid result status");
        boolean success=e.operationStatus()==InventoryOperationStatus.SUCCEEDED;
        require(success==(e.rejectionReason()==null),"Result and rejection disagree");
        if(e.rejectionReason()==InventoryRejectionReason.INVENTORY_NOT_FOUND)
            require(e.inventoryId()==null && e.remainingQuantity()==null,"Missing inventory contains a snapshot");
        else require(e.inventoryId()!=null && e.remainingQuantity()!=null && e.remainingQuantity()>=0,"Missing/invalid inventory snapshot");
        if(e.operationType()==InventoryOperationType.RESTORE) require(success && e.originalOperationId()!=null,"Invalid restore");
        else require(e.originalOperationId()==null,"Decrease references another operation");
    }
    private Set<UUID> insertOperations(List<InventoryStreamEvent> events) {
        List<Object> args=new ArrayList<>();
        for(InventoryStreamEvent e:events) Collections.addAll(args,e.operationId(),e.idempotencyKey(),e.originalOperationId(),e.orderId(),e.hubId(),e.productId(),e.inventoryId(),
                e.operationType().name(),e.operationStatus().name(),e.rejectionReason()==null?null:e.rejectionReason().name(),e.requestedQuantity(),e.remainingQuantity(),e.createdAt(),e.createdBy(),e.updatedAt(),e.updatedBy(),payloadHash(e));
        return new HashSet<>(jdbc.query("INSERT INTO hub.p_inventory_operations(id,idempotency_key,original_operation_id,order_id,hub_id,product_id,inventory_id,operation_type,operation_status,rejection_reason,requested_quantity,remaining_quantity,created_at,created_by,updated_at,updated_by,event_payload_hash) VALUES "
                        +tuples(events.size(),17)+" ON CONFLICT(id) DO NOTHING RETURNING id",(rs,n)->rs.getObject(1,UUID.class),args.toArray()));
    }
    private void verifyExisting(List<InventoryStreamEvent> events) {
        if(events.isEmpty()) return;
        List<Object> args=events.stream().map(e->(Object)e.operationId()).toList();
        Map<UUID,InventoryStreamEvent> stored=new HashMap<>();
        Map<UUID,String> fingerprints=new HashMap<>();
        jdbc.query("SELECT * FROM hub.p_inventory_operations WHERE id IN ("+placeholders(events.size())+")",(rs,n)-> {
                    InventoryStreamEvent value=event(rs,events.getFirst().epoch());
                    fingerprints.put(value.operationId(),rs.getString("event_payload_hash"));
                    return value;
                },args.toArray()).forEach(e->stored.put(e.operationId(),e));
        for(InventoryStreamEvent e:events) require(samePayload(e,stored.get(e.operationId())) && payloadHash(e).equals(fingerprints.get(e.operationId())),"Replayed operation payload changed");
    }
    private boolean samePayload(InventoryStreamEvent a,InventoryStreamEvent b) {
        if(b==null) return false;
        return Objects.equals(a.idempotencyKey(),b.idempotencyKey()) && Objects.equals(a.originalOperationId(),b.originalOperationId())
                && Objects.equals(a.orderId(),b.orderId()) && Objects.equals(a.hubId(),b.hubId()) && Objects.equals(a.productId(),b.productId())
                && Objects.equals(a.inventoryId(),b.inventoryId()) && a.operationType()==b.operationType() && a.operationStatus()==b.operationStatus()
                && a.rejectionReason()==b.rejectionReason() && a.requestedQuantity()==b.requestedQuantity() && Objects.equals(a.remainingQuantity(),b.remainingQuantity())
                && Objects.equals(a.createdAt(),b.createdAt()) && Objects.equals(a.createdBy(),b.createdBy())
                && Objects.equals(a.updatedAt(),b.updatedAt()) && Objects.equals(a.updatedBy(),b.updatedBy());
    }
    private void validateOriginals(List<InventoryStreamEvent> added) {
        List<InventoryStreamEvent> restores=added.stream().filter(e->e.operationType()==InventoryOperationType.RESTORE).toList();
        if(restores.isEmpty()) return;
        Map<UUID,InventoryStreamEvent> originals=new HashMap<>();
        Map<UUID,Long> positions=new HashMap<>();
        added.forEach(e -> positions.put(e.operationId(),e.sequence()));
        jdbc.query("SELECT * FROM hub.p_inventory_operations WHERE id IN ("+placeholders(restores.size())+")",(rs,n)->event(rs,restores.getFirst().epoch()),
                restores.stream().map(e->(Object)e.originalOperationId()).toArray()).forEach(e->originals.put(e.operationId(),e));
        for(InventoryStreamEvent e:restores) {
            InventoryStreamEvent original=originals.get(e.originalOperationId());
            require(!positions.containsKey(e.originalOperationId()) || positions.get(e.originalOperationId()) < e.sequence(),"Restore precedes original decrease");
            require(original!=null && original.operationType()==InventoryOperationType.DECREASE && original.operationStatus()==InventoryOperationStatus.SUCCEEDED
                    && Objects.equals(original.inventoryId(),e.inventoryId()) && Objects.equals(original.orderId(),e.orderId())
                    && Objects.equals(original.hubId(),e.hubId()) && Objects.equals(original.productId(),e.productId())
                    && original.requestedQuantity()==e.requestedQuantity(),"Restore does not match successful original");
        }
    }
    private void updateStocks(List<InventoryStreamEvent> changes) {
        Map<UUID,List<InventoryStreamEvent>> groups=changes.stream().collect(Collectors.groupingBy(InventoryStreamEvent::inventoryId,TreeMap::new,Collectors.toList()));
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,hub_id,product_id,quantity FROM hub.p_inventories WHERE id IN ("+placeholders(groups.size())+") ORDER BY id FOR UPDATE",groups.keySet().toArray());
        require(rows.size()==groups.size(),"An accepted inventory is missing");
        List<Object> args=new ArrayList<>();
        for(Map<String,Object> row:rows) {
            UUID id=(UUID)row.get("id"); long quantity=((Number)row.get("quantity")).longValue(); long delta=0; UUID actor=null;
            List<InventoryStreamEvent> events=groups.get(id);
            for(InventoryStreamEvent e:events) {
                require(Objects.equals(row.get("hub_id"),e.hubId()) && Objects.equals(row.get("product_id"),e.productId()),"Inventory ownership changed");
                if(e.stockChange()==0) require(e.rejectionReason()==InventoryRejectionReason.INSUFFICIENT_STOCK
                        && quantity < e.requestedQuantity(),"Invalid shortage event");
                quantity+=e.stockChange(); delta+=e.stockChange();
                require(quantity>=0 && quantity<=Integer.MAX_VALUE && quantity==e.remainingQuantity(),"Event quantity disagrees with DB checkpoint");
                if(e.stockChange()!=0 && e.actorId()!=null) actor=e.actorId();
            }
            if(events.stream().anyMatch(e->e.stockChange()!=0)) Collections.addAll(args,id,delta,events.stream().filter(e->e.stockChange()!=0).toList().getLast().occurredAt(),actor);
        }
        if(args.isEmpty()) return;
        int changedGroups=args.size()/4;
        String tuple="(?::uuid,?::bigint,?::timestamp,?::uuid)";
        int updated=jdbc.update("UPDATE hub.p_inventories i SET quantity=(i.quantity+v.delta)::integer,"
                +" updated_at=CASE WHEN i.deleted_at IS NULL THEN v.changed_at ELSE i.updated_at END,"
                +" updated_by=CASE WHEN i.deleted_at IS NULL THEN COALESCE(v.actor,i.updated_by) ELSE i.updated_by END"
                +" FROM (VALUES "+String.join(",",Collections.nCopies(changedGroups,tuple))+") v(id,delta,changed_at,actor) WHERE i.id=v.id AND i.quantity+v.delta BETWEEN 0 AND 2147483647",args.toArray());
        require(updated==changedGroups,"Inventory batch row count mismatch");
    }
    private void insertHistories(List<InventoryStreamEvent> changes) {
        List<Object> args=new ArrayList<>();
        for(InventoryStreamEvent e:changes) Collections.addAll(args,UUID.randomUUID(),e.operationId(),e.inventoryId(),e.orderId(),e.operationType().name(),e.stockChange(),e.remainingQuantity(),e.createdAt(),e.createdBy(),e.updatedAt(),e.updatedBy());
        require(jdbc.update("INSERT INTO hub.p_inventory_histories(id,operation_id,inventory_id,order_id,change_type,stock_change,stock_after,created_at,created_by,updated_at,updated_by) VALUES "+tuples(changes.size(),11),args.toArray())==changes.size(),"History batch count mismatch");
    }
    private static String payloadHash(InventoryStreamEvent e) {
        try {
            var bytes=new java.io.ByteArrayOutputStream();
            var output=new java.io.DataOutputStream(bytes);
            Object[] fields={e.epoch(),e.sequence(),e.operationId(),e.idempotencyKey(),e.originalOperationId(),e.orderId(),
                    e.hubId(),e.productId(),e.inventoryId(),e.operationType(),e.operationStatus(),e.rejectionReason(),
                    e.requestedQuantity(),e.remainingQuantity(),e.actorId(),e.occurredAt(),e.createdAt(),e.createdBy(),e.updatedAt(),e.updatedBy()};
            for(Object field:fields) {
                output.writeBoolean(field!=null);
                if(field!=null) output.writeUTF(field.toString());
            }
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch(java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Cannot fingerprint inventory event",impossible);
        }
    }
    private static String tuples(int rows,int columns) { return String.join(",",Collections.nCopies(rows,"("+placeholders(columns)+")")); }
    private static String placeholders(int count) { return String.join(",",Collections.nCopies(count,"?")); }
    private static int compareIds(String a,String b) {
        String[] aa=a.split("-"),bb=b.split("-");
        int millis=Long.compare(Long.parseLong(aa[0]),Long.parseLong(bb[0]));
        return millis==0?Long.compare(Long.parseLong(aa[1]),Long.parseLong(bb[1])):millis;
    }
    private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
}
