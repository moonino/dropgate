local RETENTION_MILLIS = 7 * 24 * 60 * 60 * 1000
local DROP_NOT_OPEN = -1
local NOT_ADMITTED = -2
local ALREADY_PURCHASED = -3
local SOLD_OUT = -4

local status = redis.call('HGET', KEYS[4], 'status')
local close_at = tonumber(redis.call('HGET', KEYS[4], 'close_at'))
if not (status == 'OPEN' or status == 'SOLD_OUT') or tonumber(ARGV[2]) >= close_at then
    return {DROP_NOT_OPEN, 'DROP_NOT_OPEN'}
end
if ARGV[3] == '1' and redis.call('EXISTS', KEYS[3]) == 0 then
    return {NOT_ADMITTED, 'NOT_ADMITTED'}
end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return {ALREADY_PURCHASED, 'ALREADY_PURCHASED'}
end
if tonumber(redis.call('GET', KEYS[1])) <= 0 then
    return {SOLD_OUT, 'SOLD_OUT'}
end

local remaining = redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('PEXPIREAT', KEYS[2], close_at + RETENTION_MILLIS)
if ARGV[3] == '1' then
    redis.call('DEL', KEYS[3])
end
if remaining == 0 then
    redis.call('HSET', KEYS[4], 'status', 'SOLD_OUT')
end
return {remaining, 'OK'}
