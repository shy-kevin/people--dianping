package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;  // 构造函数注入

    CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 把一个key，value存入redis中，并设置过期时间
    public void set(String key, Object value, Long time , TimeUnit timeUnit) {
        Random rand = new Random();
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time+rand.nextInt(20),timeUnit);  // 加上随机的过期时间，防止雪崩
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));  //TimeUnit的toSeconds方法将对应的时间转为秒
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 解决缓存穿透和雪崩的查询方法
    public <R,ID> R queryWithPassThrough(String keyPrefix , ID id , Class<R> type , Function<ID,R> dbFallback , Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 从redis中查询商铺信息
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        if(StrUtil.isNotBlank(json)){
            R r = JSONUtil.toBean(json, type);
            // 存在就直接返回
            return r;
        }
        // 因为如果String是空字符串，isNotBlank也会返回false，所以还要在这里判断stringJson是否是“”
        if(json != null){
            return null;
        }

        // 不存在就去数据库查询
        R r = dbFallback.apply(id);
        // 数据库查询存在就将数据写入redis并返回
        if(r != null){
            // rand生成随机数，设置随机ttl，防止缓存雪崩
            set(key, r, time , timeUnit);
            return r;
        }
        // 不存在就返回  这里将空值存入redis中，防止缓存穿透
        set(key, "", time , timeUnit);
        return null;
    }

    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);  // 定义一个有十个线程的线程池

    public <R,ID> R queryWithLogicalExpire(String keyPrefix , String lockPrefix , ID id , Class<R> type , Function<ID,R> dbFallback , Long time, TimeUnit timeUnit) {
        // 从redis中查询商铺信息
        String key = keyPrefix + id;
        String Json = stringRedisTemplate.opsForValue().get(key);
        // 如果未命中返回空
        if (StrUtil.isBlank(Json)) {
            return null;
        }
        // 命中了就判断逻辑过期时间
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //没过期就返回商品信息
            return r;
        }
        // 过期了尝试获取锁
        boolean flag = trylock(lockPrefix + id);
        if (flag){
            // 获取到锁,开启独立线程进行缓存重建
            executorService.submit(() -> {
                try {
                    // 重构缓存，先查数据库，在写入缓存
                    // 查数据库
                    R r1 = dbFallback.apply(id);
                    // 写入缓存
                    setWithLogicalExpire(key, r1 , time , timeUnit);
                    Thread.sleep(200);  // 模拟缓存重建的时间
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockPrefix + id);
                }
            });
        }
        // 不管有没有获取到锁，都直接返回旧的数据
        return r;
    }

    private boolean trylock(String lockKey){
        // 用redis的setnx 设置锁
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        return b != null && b;
    }

    private void unlock(String lockKey){
        stringRedisTemplate.delete(lockKey);
    }

}
