local RESTORATION_UNAVAILABLE = -1
local REOPENED = 1
local RESTORED_WITHOUT_REOPENING = 0

if redis.call('EXISTS', KEYS[1]) == 0 or redis.call('EXISTS', KEYS[3]) == 0 then
    return RESTORATION_UNAVAILABLE
end

redis.call('INCR', KEYS[1])
redis.call('SREM', KEYS[2], ARGV[1])
if redis.call('HGET', KEYS[3], 'status') == 'SOLD_OUT' then
    redis.call('HSET', KEYS[3], 'status', 'OPEN')
    return REOPENED
end
return RESTORED_WITHOUT_REOPENING
