package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryShopById(Long id) {
//        // 从redis中查询商铺信息
//        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//        // 判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            // 存在就直接返回
//            if(shop != null)
//                return Result.ok(shop);
//        }
//        // 因为如果String是空字符串，isNotBlank也会返回false，所以还要在这里判断stringJson是否是“”
//        if(shopJson != null){
//            return Result.fail("店铺不存在!!!");
//        }
//
//        // 不存在就去数据库查询
//        Shop shop = getById(id);
//        // 数据库查询存在就将数据写入redis并返回
//        Random rand = new Random();
//        if(shop != null){
//            // rand生成随机数，设置随机ttl，防止缓存雪崩
//            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL + rand.nextLong(10), TimeUnit.MINUTES);
//            return Result.ok(shop);
//        }
//        // 不存在就返回  这里将空值存入redis中，防止缓存穿透
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//        return Result.fail("店铺不存在!!!");
        // 解决缓存穿透和缓存雪崩
//        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,id2 ->(getById(id)),10L,TimeUnit.MINUTES);

        // 在前面的基础上加互斥锁解决缓存击穿问题
//        Shop shop = queryWithMutx(id);
//        if(shop == null){
//            return Result.fail("店铺不存在!!!");
//        }

        // 逻辑过期来解决缓存击穿问题
//        Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY,RedisConstants.LOCK_SHOP_KEY,id,Shop.class,id2->(getById(id2)),20l,TimeUnit.SECONDS);

        return Result.ok(shop);
    }

    @Override
    public Result updateShop(Shop shop) {
        // 先判断Shopid是否为空
        if(shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        // 先更新数据库
        updateById(shop);
        // 在删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    public Shop queryWithPassThrough(Long id) {
        // 从redis中查询商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            // 存在就直接返回
            return shop;
        }
        // 因为如果String是空字符串，isNotBlank也会返回false，所以还要在这里判断stringJson是否是“”
        if(shopJson != null){
            return null;
        }

        // 不存在就去数据库查询
        Shop shop = getById(id);
        // 数据库查询存在就将数据写入redis并返回
        Random rand = new Random();
        if(shop != null){
            // rand生成随机数，设置随机ttl，防止缓存雪崩
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL + rand.nextInt(10), TimeUnit.MINUTES);
            return shop;
        }
        // 不存在就返回  这里将空值存入redis中，防止缓存穿透
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        return null;
    }

    public Shop queryWithMutx(Long id) {
        Random rand = new Random();
        while(true){
            // 从redis中查询商铺信息
            String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
            // 判断是否存在
            if(StrUtil.isNotBlank(shopJson)){
                Shop shop = JSONUtil.toBean(shopJson, Shop.class);
                // 存在就直接返回
                if(shop != null)
                    return shop;
            }
            // 因为如果String是空字符串，isNotBlank也会返回false，所以还要在这里判断stringJson是否是“”
            if(shopJson != null){
                return null;
            }
            // 不存在就去数据库查询,只用一个线程去执行就行，所以设置互斥锁，防止多线程一起访问数据库，造成数据库访问量太大
            boolean flag = trylock("lock:shop:"+id);
            if(!flag){ // 如果没有获取到锁，就休眠一段随机时间
                try {
                    Thread.sleep(rand.nextInt(100));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }
            break;   // 视频中用递归来实现不断查询，这里用while来实现不断查询
        }

        try {
            // 如果获取到了锁
            Shop shop = getById(id);
            System.out.println("查询了一次数据库");
            // 模拟重建延时
            Thread.sleep(200);
            // 数据库查询存在就将数据写入redis并返回
            if(shop != null){
                // rand生成随机数，设置随机ttl，防止缓存雪崩
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL + rand.nextInt(10), TimeUnit.MINUTES);
                return shop;
            }
            // 不存在就返回  这里将空值存入redis中，防止缓存穿透
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            unlock("lock:shop:"+id);
        }
    }

    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);  // 定义一个有十个线程的线程池

    public Shop queryWithLogicalExpire(Long id) {
        Random rand = new Random();
        // 从redis中查询商铺信息
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 如果未命中返回空
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 命中了就判断逻辑过期时间
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //没过期就返回商品信息
            return shop;
        }
        // 过期了尝试获取锁
        boolean flag = trylock(RedisConstants.LOCK_SHOP_KEY + id);
        if (flag){
            // 获取到锁,开启独立线程进行缓存重建
            executorService.submit(() -> {
                try {
                    saveShop2Redis(id,40L);
                    Thread.sleep(200);  // 模拟缓存重建的时间
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(RedisConstants.LOCK_SHOP_KEY + id);
                }
            });
        }
        // 不管有没有获取到锁，都直接返回旧的数据
        return shop;
    }

    private boolean trylock(String lockKey){
        // 用redis的setnx 设置锁
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        return b != null && b;
    }

    private void unlock(String lockKey){
        stringRedisTemplate.delete(lockKey);
    }

    public void saveShop2Redis(Long id,Long expireSeconds) {
        // 查询店铺数据
        Shop shop = getById(id);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 存入redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
}
