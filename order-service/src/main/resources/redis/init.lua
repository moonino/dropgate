local RETENTION_MILLIS = 7 * 24 * 60 * 60 * 1000

if redis.call('EXISTS', KEYS[2]) == 1 then
    return 0
end

redis.call('HSET', KEYS[2], 'status', 'OPEN', 'open_at', ARGV[2], 'close_at', ARGV[3], 'rate', ARGV[4])
redis.call('SET', KEYS[1], ARGV[1], 'NX')
local expires_at = tonumber(ARGV[3]) + RETENTION_MILLIS
redis.call('PEXPIREAT', KEYS[1], expires_at)
redis.call('PEXPIREAT', KEYS[2], expires_at)
return 1
