if redis.call('TYPE', KEYS[1]).ok ~= 'hash' or redis.call('HGET', KEYS[1], 'epoch') ~= ARGV[1]
    or redis.call('HGET', KEYS[1], 'status') ~= 'READY'
    then return 0 end
for i=2,7 do
    if redis.call('TYPE',KEYS[i]).ok ~= 'hash' or redis.call('HEXISTS',KEYS[i],'') ~= 1 then return 0 end
end
if redis.call('TYPE',KEYS[8]).ok ~= 'stream' then return 0 end
local quantity = tonumber(ARGV[4])
if not quantity or quantity < 0 or quantity > 2147483647 or quantity ~= math.floor(quantity) then return 0 end
local known = redis.call('HGET', KEYS[3], ARGV[3])
if known then
    if known ~= ARGV[2] or redis.call('HEXISTS', KEYS[2], known) == 0 then return 0 end
    return 1
end
if redis.call('HEXISTS', KEYS[2], ARGV[2]) == 1 then return 0 end
redis.call('HSET', KEYS[1], 'status', 'DIRTY')
redis.call('HSET', KEYS[2], ARGV[2], ARGV[4])
redis.call('HSET', KEYS[3], ARGV[3], ARGV[2])
if ARGV[5] == 'true' then redis.call('HSET', KEYS[7], ARGV[6], '1') end
redis.call('HSET', KEYS[1], 'status', 'READY')
return 1
