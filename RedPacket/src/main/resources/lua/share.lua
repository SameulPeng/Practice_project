if redis.call('llen', KEYS[1]) == 0 then
    return "0"
end
local entry = redis.call('hget', KEYS[2], ARGV[1])
if entry then
    return entry
else
    local share = redis.call('lpop', KEYS[1])
    local shareAndTimeCost = share .. "-" .. ARGV[2]
    redis.call('hset', KEYS[2], ARGV[1], shareAndTimeCost)
    return share
end