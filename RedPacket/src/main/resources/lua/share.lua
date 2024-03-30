if redis.call('llen', KEYS[1]) == 0 then
    return "0"
end
local entry = redis.call('hget', KEYS[2], ARGV[1])
if entry then -- 不能使用 entry ~= nil 进行判断，此表达式总是为真
    return entry
else
    local share = redis.call('lpop', KEYS[1])
    local shareAndTimeCost = share .. "-" .. ARGV[2]
    redis.call('hset', KEYS[2], ARGV[1], shareAndTimeCost)
    return share
end