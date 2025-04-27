package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.SimpleRedisLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Random;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Test
    public void contextLoads() {
        SimpleRedisLock s1 = new SimpleRedisLock("order"+1,stringRedisTemplate);
        SimpleRedisLock s2 = new SimpleRedisLock("order"+2,stringRedisTemplate);
        s1.tryLock(1000L);
        s2.tryLock(1000L);
    }

}
