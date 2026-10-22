package com.hub.inventory.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hub.inventory.domain.stream.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import java.time.Duration;
import java.util.*;
import static com.hub.inventory.domain.stream.InventoryPipelineException.Reason.*;

@Repository
@ConditionalOnProperty(name = "inventory.stock.mode", havingValue = "redis-streams")
public class RedisInventoryReservationRepository implements InventoryReservationRepository {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String prefix, consumer;
    private final long softLimit, hardLimit, maxAgeMillis;
    private final DefaultRedisScript<List> reserve;
    private volatile UUID activeEpoch;
    private static final String GROUP = "inventory-persistence";
    private static final Duration IDLE_READ_TIMEOUT = Duration.ofMillis(250);
    public RedisInventoryReservationRepository(StringRedisTemplate redis, ObjectMapper mapper,
            @Value("${inventory.streams.prefix:inventory:streams}") String prefix,
            @Value("${inventory.streams.consumer:inventory-consumer}") String consumer,
            @Value("${inventory.streams.soft-limit:10000}") long softLimit,
            @Value("${inventory.streams.hard-limit:20000}") long hardLimit,
            @Value("${inventory.streams.max-age-millis:10000}") long maxAgeMillis) {
        this.redis=redis; this.mapper=mapper; this.prefix=prefix; this.consumer=consumer;
        this.softLimit=softLimit; this.hardLimit=hardLimit; this.maxAgeMillis=maxAgeMillis;
        this.reserve = new DefaultRedisScript<>(); reserve.setLocation(new ClassPathResource("inventory/reserve.lua")); reserve.setResultType(List.class);
    }
    public void bootstrap(PipelineSnapshot snapshot) {
        verifyPersistence();
        PipelineState current = state();
        if (current.status() != PipelineState.Status.MISSING) {
            if (snapshot.epoch().equals(current.epoch()) && current.status() == PipelineState.Status.READY) { checkedState(); activeEpoch=current.epoch(); return; }
            throw unavailable();
        }
        Long claimed = script("if redis.call('EXISTS',KEYS[1])==1 then return 0 end redis.call('HSET',KEYS[1],'epoch',ARGV[1],'status','PAUSED','revision',ARGV[2],'confirmed',ARGV[2],'streamId',ARGV[3]) return 1",
                List.of(engine()), snapshot.epoch().toString(), Long.toString(snapshot.sequence()), snapshot.streamId());
        if (!Long.valueOf(1).equals(claimed)) throw unavailable();
        initialize(snapshot.epoch(), snapshot.inventories(), snapshot.operations(), List.of());
        activate(snapshot.epoch(), snapshot.sequence(), snapshot.sequence(), snapshot.streamId());
    }
    public PipelineState state() {
        try {
            Map<Object,Object> values = redis.opsForHash().entries(engine());
            if (values.isEmpty()) return new PipelineState(null, PipelineState.Status.MISSING, 0, 0, "0-0", 0);
            UUID epoch = UUID.fromString((String)values.get("epoch"));
            var status = PipelineState.Status.valueOf((String)values.get("status"));
            long revision = Long.parseLong((String)values.get("revision"));
            long confirmed = Long.parseLong((String)values.get("confirmed"));
            String streamId = (String)values.get("streamId");
            long oldest = 0;
            if (revision > confirmed && redis.type(key(epoch,"stream")) == org.springframework.data.redis.connection.DataType.STREAM) {
                var entries = redis.opsForStream().range(key(epoch,"stream"), org.springframework.data.domain.Range.unbounded(), org.springframework.data.redis.connection.Limit.limit().count(1));
                if (entries != null && !entries.isEmpty()) oldest = Long.parseLong(entries.getFirst().getId().getValue().split("-")[0]);
            }
            return new PipelineState(epoch,status,revision,confirmed,streamId,oldest);
        } catch (RuntimeException e) { throw wrap(e); }
    }
    public void loadInventory(InventorySeed seed) {
        UUID active=epoch();
        Long loaded=scriptFile("inventory/load.lua",keys(active),
                active.toString(), seed.inventoryId().toString(), seed.hubId()+":"+seed.productId(), Integer.toString(seed.quantity()), Boolean.toString(seed.deleted()),seed.productId().toString());
        if (!Long.valueOf(1).equals(loaded)) throw unavailable();
    }
    public boolean inventoryKnown(UUID hubId, UUID productId) {
        UUID epoch=epoch();
        Long result=scriptFile("inventory/inspect.lua",keys(epoch),epoch.toString(),hubId+":"+productId);
        if(result==null || result<0) throw unavailable();
        return result==1;
    }
    public InventoryStreamEvent execute(ReservationRequest request) {
        if(request==null || request.idempotencyKey()==null || request.idempotencyKey().isBlank()
                || request.idempotencyKey().length()>120) throw unavailable();
        UUID epoch=epoch();
        try {
            List<?> result = redis.execute(reserve, keys(epoch),epoch.toString(),json(request),Long.toString(softLimit),Long.toString(hardLimit),Long.toString(maxAgeMillis));
            if (result == null || result.size()!=2) throw unavailable();
            if (!"OK".equals(result.getFirst())) throw new InventoryPipelineException(InventoryPipelineException.Reason.valueOf((String)result.get(1)));
            return event((String)result.get(1));
        } catch (RuntimeException e) { throw wrap(e); }
    }
    public Optional<InventoryStreamEvent> findOperation(UUID id) {
        PipelineState state=checkedState(); Object value=redis.opsForHash().get(key(state.epoch(),"operations"),id.toString());
        return value==null ? Optional.empty() : Optional.of(event((String)value));
    }
    public Map<UUID,Integer> quantities(List<UUID> ids) {
        UUID epoch=epoch();
        List<String> args=new ArrayList<>(); args.add(epoch.toString());
        ids.forEach(id -> args.add(id.toString()));
        DefaultRedisScript<List> query=new DefaultRedisScript<>();
        query.setLocation(new ClassPathResource("inventory/quantities.lua")); query.setResultType(List.class);
        try {
            List<?> values=redis.execute(query,keys(epoch),args.toArray());
            if(values==null || values.size()!=ids.size()+1 || !"OK".equals(values.getFirst())) throw unavailable();
            Map<UUID,Integer> result=new HashMap<>();
            for(int i=0;i<ids.size();i++) result.put(ids.get(i),Integer.parseInt((String)values.get(i+1)));
            return Map.copyOf(result);
        } catch(RuntimeException e) { throw wrap(e); }
    }
    public void tombstoneProduct(UUID id) {
        PipelineState state=checkedState();
        Long changed=script("if redis.call('HGET',KEYS[1],'epoch')~=ARGV[1] or redis.call('HGET',KEYS[1],'status')~='READY' or redis.call('TYPE',KEYS[2]).ok~='hash' then return 0 end redis.call('HSET',KEYS[1],'status','DIRTY') redis.call('HSET',KEYS[2],ARGV[2],'1') redis.call('HSET',KEYS[1],'status','READY') return 1",
                List.of(engine(),key(state.epoch(),"deleted")),state.epoch().toString(),id.toString());
        if(!Long.valueOf(1).equals(changed)) throw unavailable();
    }
    public boolean isProductDeleted(UUID id) { PipelineState state=checkedState(); return redis.opsForHash().hasKey(key(state.epoch(),"deleted"),id.toString()); }
    public List<StreamEntry> read(int count) {
        PipelineState state=checkedState();
        try {
            String stream=key(state.epoch(),"stream");
            List<MapRecord<String,Object,Object>> pending=redis.opsForStream().read(Consumer.from(GROUP,consumer),StreamReadOptions.empty().count(count),StreamOffset.create(stream,ReadOffset.from("0-0")));
            if(pending!=null && !pending.isEmpty()) return entries(pending);
            List<MapRecord<String,Object,Object>> available=redis.opsForStream().read(Consumer.from(GROUP,consumer),StreamReadOptions.empty().count(count),StreamOffset.create(stream,ReadOffset.lastConsumed()));
            if(available!=null && !available.isEmpty()) return entries(available);
            // 비어 있을 때만 첫 메시지를 기다린다. Lettuce가 BLOCK 명령에 전용 연결을 사용한다.
            return entries(redis.opsForStream().read(Consumer.from(GROUP,consumer),
                    StreamReadOptions.empty().count(count).block(IDLE_READ_TIMEOUT),
                    StreamOffset.create(stream,ReadOffset.lastConsumed())));
        } catch(RuntimeException e) { throw wrap(e); }
    }
    private void verifyPersistence() {
        try {
            redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                var commands=connection.serverCommands();
                if(!"yes".equals(commands.getConfig("appendonly").getProperty("appendonly"))
                        || !"always".equals(commands.getConfig("appendfsync").getProperty("appendfsync"))
                        || !"noeviction".equals(commands.getConfig("maxmemory-policy").getProperty("maxmemory-policy"))) throw unavailable();
                return null;
            });
        } catch(RuntimeException e) { throw wrap(e); }
    }
    public void confirm(UUID epoch,long sequence,String streamId) {
        Long result=scriptFile("inventory/confirm.lua",List.of(engine(),key(epoch,"stream")),epoch.toString(),Long.toString(sequence),streamId,GROUP);
        if(!Long.valueOf(1).equals(result)) throw unavailable();
    }
    public void pause() {
        Long result=script("if redis.call('TYPE',KEYS[1]).ok~='hash' then return 0 end redis.call('HSET',KEYS[1],'status','PAUSED') return 1",List.of(engine()));
        if(!Long.valueOf(1).equals(result)) throw unavailable();
    }
    public void rebuild(PipelineSnapshot snapshot,List<StreamEntry> events,UUID epoch) {
        PipelineState old=state();
        if(old.status()==PipelineState.Status.MISSING || !snapshot.epoch().equals(old.epoch()) || epoch.equals(old.epoch())) throw unavailable();
        pause();
        if(redis.type(key(old.epoch(),"deleted"))!=org.springframework.data.redis.connection.DataType.HASH) throw unavailable();
        Map<Object,Object> tombstones=redis.opsForHash().entries(key(old.epoch(),"deleted"));
        if(!tombstones.containsKey("")) throw unavailable();
        Map<UUID,InventorySeed> seeds=new LinkedHashMap<>();
        for(var seed:snapshot.inventories()) seeds.put(seed.inventoryId(),seed);
        List<InventoryStreamEvent> replay=new ArrayList<>(); long sequence=0, previous=snapshot.sequence();
        Set<UUID> operationIds=new HashSet<>(); snapshot.operations().forEach(e->operationIds.add(e.operationId()));
        for(var entry:events) {
            var event=entry.event();
            if(!snapshot.epoch().equals(event.epoch()) || event.sequence()<=snapshot.sequence()) continue;
            if(event.sequence()!=previous+1 || !operationIds.add(event.operationId())) throw unavailable();
            previous=event.sequence();
            if(event.stockChange()!=0) {
                var seed=seeds.get(event.inventoryId()); if(seed==null) throw unavailable();
                long after=(long)seed.quantity()+event.stockChange();
                if(after<0 || after>Integer.MAX_VALUE || !Objects.equals(event.remainingQuantity(),(int)after)) throw unavailable();
                seeds.put(seed.inventoryId(),new InventorySeed(seed.inventoryId(),seed.hubId(),seed.productId(),(int)after,seed.deleted()));
            }
            replay.add(resequence(event,epoch,++sequence));
        }
        if(snapshot.sequence()>old.revision() || previous<old.revision()) throw unavailable();
        initialize(epoch,List.copyOf(seeds.values()),snapshot.operations(),replay);
        redis.opsForHash().putAll(key(epoch,"deleted"),tombstones);
        activate(epoch,sequence,0,"0-0");
    }
    public List<StreamEntry> unconfirmed() {
        PipelineState state=state();
        if(state.status()==PipelineState.Status.MISSING) throw unavailable();
        try {
            if(redis.type(key(state.epoch(),"stream"))!=org.springframework.data.redis.connection.DataType.STREAM) throw unavailable();
            return entries(redis.opsForStream().range(key(state.epoch(),"stream"),org.springframework.data.domain.Range.unbounded()));
        }
        catch(RuntimeException e) { throw wrap(e); }
    }
    private void initialize(UUID epoch,List<InventorySeed> inventories,List<InventoryStreamEvent> operations,List<InventoryStreamEvent> replay) {
        if(redis.hasKey(key(epoch,"stocks"))) throw unavailable();
        for(String name:List.of("stocks","routes","operations","idempotency","restored","deleted")) redis.opsForHash().put(key(epoch,name),"","1");
        for(var seed:inventories) {
            if(seed.quantity()<0) throw unavailable();
            redis.opsForHash().put(key(epoch,"stocks"),seed.inventoryId().toString(),Integer.toString(seed.quantity()));
            redis.opsForHash().put(key(epoch,"routes"),seed.hubId()+":"+seed.productId(),seed.inventoryId().toString());
            if(seed.deleted()) redis.opsForHash().put(key(epoch,"deleted"),seed.productId().toString(),"1");
        }
        for(var operation:operations) index(epoch,operation);
        redis.opsForStream().createGroup(key(epoch,"stream"),ReadOffset.from("0-0"),GROUP);
        for(var operation:replay) { index(epoch,operation); redis.opsForStream().add(key(epoch,"stream"),Map.of("event",json(operation))); }
    }
    private void index(UUID epoch,InventoryStreamEvent event) {
        String id=event.operationId().toString();
        Object existing=redis.opsForHash().get(key(epoch,"idempotency"),event.idempotencyKey());
        if(existing!=null && !id.equals(existing)) throw unavailable();
        redis.opsForHash().put(key(epoch,"operations"),id,json(event));
        redis.opsForHash().put(key(epoch,"idempotency"),event.idempotencyKey(),id);
        if(event.originalOperationId()!=null) {
            Object restored=redis.opsForHash().get(key(epoch,"restored"),event.originalOperationId().toString());
            if(restored!=null && !id.equals(restored)) throw unavailable();
            redis.opsForHash().put(key(epoch,"restored"),event.originalOperationId().toString(),id);
        }
    }
    private void activate(UUID epoch,long revision,long confirmed,String streamId) {
        Long result=script("if redis.call('TYPE',KEYS[1]).ok~='hash' or redis.call('HGET',KEYS[1],'status')~='PAUSED' then return 0 end redis.call('HSET',KEYS[1],'epoch',ARGV[1],'revision',ARGV[2],'confirmed',ARGV[3],'streamId',ARGV[4],'status','READY') return 1",
                List.of(engine()),epoch.toString(),Long.toString(revision),Long.toString(confirmed),streamId);
        if(!Long.valueOf(1).equals(result)) throw unavailable();
        activeEpoch=epoch;
    }
    private UUID epoch() {
        UUID epoch=activeEpoch;
        if(epoch==null) { var state=checkedState(); epoch=state.epoch(); activeEpoch=epoch; }
        return epoch;
    }
    private PipelineState checkedState() {
        PipelineState state=state(); if(state.status()!=PipelineState.Status.READY) throw unavailable();
        Long result=scriptFile("inventory/inspect.lua",keys(state.epoch()),state.epoch().toString(),"");
        if(!Long.valueOf(1).equals(result)) throw unavailable();
        return state;
    }
    private List<String> keys(UUID epoch) { return List.of(engine(),key(epoch,"stocks"),key(epoch,"routes"),key(epoch,"operations"),key(epoch,"idempotency"),key(epoch,"restored"),key(epoch,"deleted"),key(epoch,"stream")); }
    private String key(UUID epoch,String suffix) { return prefix+":"+epoch+":"+suffix; }
    private String engine() { return prefix+":engine"; }
    private Long script(String source,List<String> keys,String...args) { try { return redis.execute(new DefaultRedisScript<>(source,Long.class),keys,(Object[])args); } catch(RuntimeException e) { throw wrap(e); } }
    private Long scriptFile(String path,List<String> keys,String...args) { DefaultRedisScript<Long> script=new DefaultRedisScript<>(); script.setLocation(new ClassPathResource(path)); script.setResultType(Long.class); try { return redis.execute(script,keys,(Object[])args); } catch(RuntimeException e) { throw wrap(e); } }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch(JsonProcessingException e) { throw new InventoryPipelineException(UNAVAILABLE,e); } }
    private InventoryStreamEvent event(String value) { try { return mapper.readValue(value,InventoryStreamEvent.class); } catch(JsonProcessingException e) { throw new InventoryPipelineException(UNAVAILABLE,e); } }
    private List<StreamEntry> entries(List<MapRecord<String,Object,Object>> records) {
        if(records==null) return List.of();
        List<StreamEntry> parsed=new ArrayList<>();
        for(var record:records) {
            try { parsed.add(new StreamEntry(record.getId().getValue(),event((String)record.getValue().get("event")))); }
            catch(RuntimeException failure) { throw new InvalidInventoryStreamEventException(parsed,record.getId().getValue(),failure); }
        }
        return List.copyOf(parsed);
    }
    private InventoryStreamEvent resequence(InventoryStreamEvent e,UUID epoch,long sequence) { return new InventoryStreamEvent(epoch,sequence,e.operationId(),e.idempotencyKey(),e.originalOperationId(),e.orderId(),e.hubId(),e.productId(),e.inventoryId(),e.operationType(),e.operationStatus(),e.rejectionReason(),e.requestedQuantity(),e.remainingQuantity(),e.actorId(),e.occurredAt(),e.createdAt(),e.createdBy(),e.updatedAt(),e.updatedBy()); }
    private InventoryPipelineException unavailable() { return new InventoryPipelineException(UNAVAILABLE); }
    private InventoryPipelineException wrap(RuntimeException e) { return e instanceof InventoryPipelineException pipeline ? pipeline : new InventoryPipelineException(UNAVAILABLE,e); }
}
