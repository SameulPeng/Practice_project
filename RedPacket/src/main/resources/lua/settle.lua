if redis.call('ttl', KEYS[1]) == -1 then
    redis.call('expire', KEYS[1], ARGV[1])
    return 1
else
    return 0
end