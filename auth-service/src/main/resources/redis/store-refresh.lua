redis.call('SET', KEYS[1], '1', 'PX', ARGV[2])
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('PEXPIRE', KEYS[2], ARGV[2])
return 1
