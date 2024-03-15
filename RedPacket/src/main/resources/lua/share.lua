if redis.call('llen', KEYS[1]) == 0 then
    return 0
elseif redis.call('hexists', KEYS[2], ARGV[1]) == 1 then
    return -1
else
    local share = redis.call('lpop', KEYS[1])
    redis.call('hset', KEYS[2], ARGV[1], share)
    return tonumber(share)
end