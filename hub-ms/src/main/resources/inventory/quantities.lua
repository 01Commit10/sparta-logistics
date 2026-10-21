-- Validation and values belong to the same Redis execution; no DIRTY intermediate value may escape.
if redis.call('TYPE',KEYS[1]).ok ~= 'hash' or redis.call('HGET',KEYS[1],'epoch') ~= ARGV[1]
    or redis.call('HGET',KEYS[1],'status') ~= 'READY' then return {'ERROR'} end
for i=2,7 do
    if redis.call('TYPE',KEYS[i]).ok ~= 'hash' or redis.call('HEXISTS',KEYS[i],'') ~= 1 then return {'ERROR'} end
end
if redis.call('TYPE',KEYS[8]).ok ~= 'stream' then return {'ERROR'} end
local result={'OK'}
for i=2,#ARGV do
    local quantity=tonumber(redis.call('HGET',KEYS[2],ARGV[i]))
    if not quantity or quantity<0 or quantity>2147483647 or quantity~=math.floor(quantity) then return {'ERROR'} end
    result[#result+1]=tostring(quantity)
end
return result
