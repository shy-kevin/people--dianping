package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";  // 通过设置这个UUID来判断不同的JVM,因为加了static final，所以同一个JVM内的这个值都是相同的

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;  // 初始化加载lua脚本，用静态代码块加载，不要在解锁的时候在加载提高解锁性能
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("redislock.lua"));
    }

    @Override
    public boolean tryLock(long timeout) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        String key = KEY_PREFIX + name;
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeout, TimeUnit.SECONDS);// 加锁
        return Boolean.TRUE.equals(b);  // 防止自动拆箱时空指针的风险
    }

//    @Override
//    public void unlock() {
//        String t_id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);  // redis的线程标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();   // 当前的线程标识
//        if (!threadId.equals(t_id)) {
//            return ; // 不是自己的就不用管，说明自己的锁已经超时自动释放了，现在是别人的锁
//        }
//        stringRedisTemplate.delete(KEY_PREFIX + name);   // 判断锁和删除锁最好是原子操作，不然有可能有线程安全问题
//    }

    @Override
    public void unlock() {
        // 用lua脚本来实现原子性的锁释放
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()); // Collections.singletonList(KEY_PREFIX + name)用来传入lua脚本的KEYS参数，后面的其他参数
    }

}
