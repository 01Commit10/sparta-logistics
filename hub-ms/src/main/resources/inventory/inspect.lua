if redis.call('TYPE',KEYS[1]).ok ~= 'hash' or redis.call('HGET',KEYS[1],'epoch') ~= ARGV[1]
    or redis.call('HGET',KEYS[1],'status') ~= 'READY' then return -1 end
for i=2,7 do
    if redis.call('TYPE',KEYS[i]).ok ~= 'hash' or redis.call('HEXISTS',KEYS[i],'') ~= 1 then return -1 end
end
if redis.call('TYPE',KEYS[8]).ok ~= 'stream' then return -1 end
if ARGV[2] == '' then return 1 end
local known = redis.call('HGET',KEYS[3],ARGV[2])
if not known then return 0 end
if redis.call('HEXISTS',KEYS[2],known) == 0 then return -1 end
return 1
