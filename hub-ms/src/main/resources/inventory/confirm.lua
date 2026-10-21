if redis.call('TYPE', KEYS[1]).ok ~= 'hash' or redis.call('HGET', KEYS[1], 'epoch') ~= ARGV[1]
    or redis.call('HGET', KEYS[1], 'status') ~= 'READY' or redis.call('TYPE', KEYS[2]).ok ~= 'stream' then return 0 end
local revision = tonumber(redis.call('HGET', KEYS[1], 'revision'))
local confirmed = tonumber(redis.call('HGET', KEYS[1], 'confirmed'))
local sequence = tonumber(ARGV[2])
if not sequence or sequence < confirmed or sequence > revision then return 0 end
if sequence == confirmed then
    if redis.call('HGET', KEYS[1], 'streamId') == ARGV[3] then return 1 else return 0 end
end
local entries = redis.call('XRANGE', KEYS[2], '-', ARGV[3])
local expected = confirmed
for _, entry in ipairs(entries) do
    local event = cjson.decode(entry[2][2])
    if event.epoch ~= ARGV[1] or event.sequence ~= expected+1 then return 0 end
    expected = event.sequence
end
if expected ~= sequence then return 0 end
redis.call('HSET', KEYS[1], 'status', 'DIRTY')
redis.call('HSET', KEYS[1], 'confirmed', sequence, 'streamId', ARGV[3])
for _, entry in ipairs(entries) do
    redis.call('XACK', KEYS[2], ARGV[4], entry[1])
    redis.call('XDEL', KEYS[2], entry[1])
end
redis.call('HSET', KEYS[1], 'status', 'READY')
return 1
