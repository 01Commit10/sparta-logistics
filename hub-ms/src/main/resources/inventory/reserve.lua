local engine = KEYS[1]
local function fail(reason) return {'ERROR', reason} end
local function typed(key, expected) return redis.call('TYPE', key).ok == expected end
if not typed(engine, 'hash') or redis.call('HGET', engine, 'epoch') ~= ARGV[1]
    or redis.call('HGET', engine, 'status') ~= 'READY' then return fail('UNAVAILABLE') end
for i=2,7 do if not typed(KEYS[i], 'hash') or redis.call('HEXISTS', KEYS[i], '') ~= 1 then return fail('UNAVAILABLE') end end
if not typed(KEYS[8], 'stream') then return fail('UNAVAILABLE') end
local r = cjson.decode(ARGV[2])
local function value(v) return v ~= nil and v ~= cjson.null end
local function validqty(v) return type(v) == 'number' and v > 0 and v <= 2147483647 and v == math.floor(v) end
if not value(r.operationId) or not value(r.idempotencyKey) or #r.idempotencyKey < 1
    or not value(r.occurredAt) or not value(r.createdAt) then return fail('UNAVAILABLE') end
local existing = redis.call('HGET', KEYS[5], r.idempotencyKey)
if existing then
    local payload = redis.call('HGET', KEYS[4], existing)
    if not payload then return fail('UNAVAILABLE') end
    local old = cjson.decode(payload)
    if value(old.inventoryId) then
        local oldStock = tonumber(redis.call('HGET',KEYS[2],old.inventoryId))
        if not oldStock or oldStock < 0 or oldStock > 2147483647 or oldStock ~= math.floor(oldStock) then return fail('UNAVAILABLE') end
    end
    local same = old.operationType == r.operationType
    if r.operationType == 'RESTORE' then same = same and old.originalOperationId == r.originalOperationId
    else same = same and old.orderId == r.orderId and old.hubId == r.hubId and old.productId == r.productId and old.requestedQuantity == r.quantity end
    if same then return {'OK', payload} else return fail('IDEMPOTENCY_CONFLICT') end
end
if redis.call('HEXISTS', KEYS[4], r.operationId) == 1 then return fail('IDEMPOTENCY_CONFLICT') end
local revision = tonumber(redis.call('HGET', engine, 'revision'))
local confirmed = tonumber(redis.call('HGET', engine, 'confirmed'))
if not revision or not confirmed or revision < confirmed or confirmed < 0
    or revision ~= math.floor(revision) or confirmed ~= math.floor(confirmed)
    -- Redis cjson serializes numbers with 14 significant decimal digits.
    or revision >= 99999999999999 then return fail('UNAVAILABLE') end
local backlog = revision - confirmed
local now = redis.call('TIME'); local nowms = tonumber(now[1])*1000 + math.floor(tonumber(now[2])/1000)
local first = redis.call('XRANGE', KEYS[8], '(' .. redis.call('HGET', engine, 'streamId'), '+', 'COUNT', 1)
local tooOld = #first > 0 and nowms - tonumber(string.match(first[1][1], '^(%d+)')) >= tonumber(ARGV[5])
local original = cjson.null
local inventory = cjson.null
local current = nil
local remaining = cjson.null
local status = 'SUCCEEDED'
local reason = cjson.null
local quantity = r.quantity
local order = r.orderId
local hub = r.hubId
local product = r.productId
if r.operationType == 'RESTORE' then
    if not value(r.originalOperationId) then return fail('INVALID_ORIGINAL') end
    local originalPayload = redis.call('HGET', KEYS[4], r.originalOperationId)
    if not originalPayload then return fail('ORIGINAL_NOT_FOUND') end
    original = cjson.decode(originalPayload)
    if original.operationType ~= 'DECREASE' or original.operationStatus ~= 'SUCCEEDED' then return fail('INVALID_ORIGINAL') end
    if redis.call('HEXISTS', KEYS[6], r.originalOperationId) == 1 then return fail('ALREADY_RESTORED') end
    inventory = original.inventoryId; quantity = original.requestedQuantity
    order = original.orderId; hub = original.hubId; product = original.productId
    if backlog >= tonumber(ARGV[4]) then return fail('UNAVAILABLE') end
elseif r.operationType == 'DECREASE' then
    if not value(order) or not value(hub) or not value(product) or not validqty(quantity) then return fail('UNAVAILABLE') end
    if backlog >= tonumber(ARGV[3]) or tooOld then return fail('UNAVAILABLE') end
    inventory = redis.call('HGET', KEYS[3], hub .. ':' .. product) or cjson.null
else return fail('UNAVAILABLE') end
if value(inventory) then
    current = tonumber(redis.call('HGET', KEYS[2], inventory))
    if not current or current < 0 or current > 2147483647 or current ~= math.floor(current) then return fail('UNAVAILABLE') end
end
local deleted = redis.call('HEXISTS', KEYS[7], product) == 1
if r.operationType == 'RESTORE' then
    if not current or deleted then return fail('INVENTORY_NOT_FOUND') end
    if not validqty(quantity) or current + quantity > 2147483647 then return fail('QUANTITY_LIMIT') end
    remaining = current + quantity
elseif not current or deleted then
    inventory = cjson.null; status = 'REJECTED'; reason = 'INVENTORY_NOT_FOUND'
elseif current < quantity then
    remaining = current; status = 'REJECTED'; reason = 'INSUFFICIENT_STOCK'
else remaining = current - quantity end
local event = {epoch=ARGV[1], sequence=revision+1, operationId=r.operationId, idempotencyKey=r.idempotencyKey,
    originalOperationId=r.originalOperationId, orderId=order, hubId=hub, productId=product, inventoryId=inventory,
    operationType=r.operationType, operationStatus=status, rejectionReason=reason, requestedQuantity=quantity,
    remainingQuantity=remaining, actorId=r.actorId, occurredAt=r.occurredAt, createdAt=r.createdAt, createdBy=r.createdBy,
    updatedAt=r.updatedAt, updatedBy=r.updatedBy}
local encoded = cjson.encode(event)
redis.call('HSET', engine, 'status', 'DIRTY')
if status == 'SUCCEEDED' then redis.call('HSET', KEYS[2], inventory, tostring(remaining)) end
redis.call('HSET', KEYS[4], r.operationId, encoded)
redis.call('HSET', KEYS[5], r.idempotencyKey, r.operationId)
if r.operationType == 'RESTORE' then redis.call('HSET', KEYS[6], r.originalOperationId, r.operationId) end
redis.call('XADD', KEYS[8], '*', 'event', encoded)
redis.call('HSET', engine, 'revision', revision+1, 'status', 'READY')
return {'OK', encoded}
