-- 锁的key
local key = KEYS[1]
-- 当前线程标识
local threadId = ARGV[1]

-- 获取锁的线程标识
local id = redis.call('get',KEYS[1])
if id == ARGV[1] then
    return redis.call('del',KEYS[1]) -- 删除成功返回1
end
return 0  -- 没删除返回0