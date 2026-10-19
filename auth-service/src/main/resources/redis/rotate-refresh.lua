if redis.call('DEL', KEYS[1]) == 0 then
    return 0
end
redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
redis.call('SREM', KEYS[3], ARGV[1])
redis.call('SADD', KEYS[3], ARGV[2])
redis.call('PEXPIRE', KEYS[3], ARGV[3])
return 1
