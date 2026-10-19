redis.call('SET', KEYS[1], '1', 'PX', ARGV[1])
for _, tokenId in ipairs(redis.call('SMEMBERS', KEYS[2])) do
    redis.call('DEL', ARGV[2] .. tokenId)
end
redis.call('DEL', KEYS[2])
return 1
